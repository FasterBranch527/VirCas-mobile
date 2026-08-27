package com.vircas.mobile.core.progression

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.progressionDataStore by preferencesDataStore("progression")

data class UserProgress(
    val xp: Int = 0,
    val gamesPlayed: Int = 0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val winStreak: Int = 0,
    val dailyStreak: Int = 0,
    val totalWagered: Long = 0,
    val totalWon: Long = 0,
    val biggestWin: Long = 0,
    val favoriteGame: String = "—",
    val dailyGames: Int = 0,
    val dailyWins: Int = 0,
    val dailyCases: Int = 0,
    val dailyBets: Int = 0,
    val dailyDistinctGames: Set<String> = emptySet()
) {
    val level: Int get() = 1 + xp / 1_000
    val levelXp: Int get() = xp % 1_000
    val winRate: Double get() = if (gamesPlayed == 0) 0.0 else totalWins * 100.0 / gamesPlayed
}

data class DailyRewardClaim(val day: Int, val amount: Long, val streak: Int)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val xpReward: Int,
    val unlocked: Boolean
)

data class DailyMission(
    val id: String,
    val title: String,
    val progress: Int,
    val target: Int,
    val coinReward: Long,
    val xpReward: Int
) {
    val complete: Boolean get() = progress >= target
}

class ProgressionRepository(private val context: Context) {
    private object Keys {
        val xp = intPreferencesKey("xp")
        val gamesPlayed = intPreferencesKey("games_played")
        val wins = intPreferencesKey("wins")
        val losses = intPreferencesKey("losses")
        val winStreak = intPreferencesKey("win_streak")
        val dailyStreak = intPreferencesKey("daily_streak")
        val totalWagered = longPreferencesKey("total_wagered")
        val totalWon = longPreferencesKey("total_won")
        val biggestWin = longPreferencesKey("biggest_win")
        val gameCounts = stringPreferencesKey("game_counts")
        val lastDailyEpochDay = longPreferencesKey("last_daily_epoch_day")
        val dailyCountersEpochDay = longPreferencesKey("daily_counters_epoch_day")
        val dailyGames = intPreferencesKey("daily_games")
        val dailyWins = intPreferencesKey("daily_wins")
        val dailyCases = intPreferencesKey("daily_cases")
        val dailyBets = intPreferencesKey("daily_bets")
        val dailyDistinctGames = stringPreferencesKey("daily_distinct_games")
    }

    val progress: Flow<UserProgress> = context.progressionDataStore.data.map { p ->
        val counts = decodeCounts(p[Keys.gameCounts].orEmpty())
        val favorite = counts.maxByOrNull { it.value }?.key ?: "—"
        val today = LocalDate.now().toEpochDay()
        val dailyIsCurrent = p[Keys.dailyCountersEpochDay] == today
        UserProgress(
            xp = p[Keys.xp] ?: 0,
            gamesPlayed = p[Keys.gamesPlayed] ?: 0,
            totalWins = p[Keys.wins] ?: 0,
            totalLosses = p[Keys.losses] ?: 0,
            winStreak = p[Keys.winStreak] ?: 0,
            dailyStreak = p[Keys.dailyStreak] ?: 0,
            totalWagered = p[Keys.totalWagered] ?: 0L,
            totalWon = p[Keys.totalWon] ?: 0L,
            biggestWin = p[Keys.biggestWin] ?: 0L,
            favoriteGame = favorite,
            dailyGames = if (dailyIsCurrent) p[Keys.dailyGames] ?: 0 else 0,
            dailyWins = if (dailyIsCurrent) p[Keys.dailyWins] ?: 0 else 0,
            dailyCases = if (dailyIsCurrent) p[Keys.dailyCases] ?: 0 else 0,
            dailyBets = if (dailyIsCurrent) p[Keys.dailyBets] ?: 0 else 0,
            dailyDistinctGames = if (dailyIsCurrent) decodeSet(p[Keys.dailyDistinctGames].orEmpty()) else emptySet()
        )
    }

    suspend fun recordGame(game: String, stake: Long, payout: Long, xpReward: Int = 20) {
        require(stake >= 0L && payout >= 0L)
        val today = LocalDate.now().toEpochDay()
        context.progressionDataStore.edit { p ->
            resetDailyIfNeeded(p, today)
            val won = payout > stake
            p[Keys.gamesPlayed] = (p[Keys.gamesPlayed] ?: 0) + 1
            p[Keys.xp] = (p[Keys.xp] ?: 0) + xpReward
            p[Keys.totalWagered] = (p[Keys.totalWagered] ?: 0L) + stake
            p[Keys.totalWon] = (p[Keys.totalWon] ?: 0L) + payout
            p[Keys.biggestWin] = maxOf(p[Keys.biggestWin] ?: 0L, payout)
            if (won) {
                p[Keys.wins] = (p[Keys.wins] ?: 0) + 1
                p[Keys.winStreak] = (p[Keys.winStreak] ?: 0) + 1
                p[Keys.dailyWins] = (p[Keys.dailyWins] ?: 0) + 1
            } else {
                p[Keys.losses] = (p[Keys.losses] ?: 0) + 1
                p[Keys.winStreak] = 0
            }
            p[Keys.dailyGames] = (p[Keys.dailyGames] ?: 0) + 1
            val distinct = decodeSet(p[Keys.dailyDistinctGames].orEmpty()).toMutableSet().apply { add(game) }
            p[Keys.dailyDistinctGames] = encodeSet(distinct)
            val counts = decodeCounts(p[Keys.gameCounts].orEmpty()).toMutableMap()
            counts[game] = (counts[game] ?: 0) + 1
            p[Keys.gameCounts] = encodeCounts(counts)
        }
    }

    suspend fun recordCaseOpen() = incrementDaily(Keys.dailyCases)
    suspend fun recordVirtualBet() = incrementDaily(Keys.dailyBets)

    suspend fun claimDailyReward(today: LocalDate = LocalDate.now()): DailyRewardClaim? {
        val epoch = today.toEpochDay()
        var claim: DailyRewardClaim? = null
        context.progressionDataStore.edit { p ->
            val last = p[Keys.lastDailyEpochDay]
            if (last == epoch) return@edit
            val streak = if (last == epoch - 1) (p[Keys.dailyStreak] ?: 0) + 1 else 1
            val day = ((streak - 1) % 7) + 1
            val rewards = longArrayOf(500, 750, 1_000, 1_500, 2_000, 3_000, 5_000)
            p[Keys.lastDailyEpochDay] = epoch
            p[Keys.dailyStreak] = streak
            p[Keys.xp] = (p[Keys.xp] ?: 0) + 50
            claim = DailyRewardClaim(day, rewards[day - 1], streak)
        }
        return claim
    }

    fun achievements(progress: UserProgress): List<Achievement> = listOf(
        Achievement("first_win", "First Win", "Win your first round", 100, progress.totalWins >= 1),
        Achievement("five_wins", "On a Roll", "Win 5 games", 150, progress.totalWins >= 5),
        Achievement("hundred_games", "Arcade Regular", "Play 100 rounds", 400, progress.gamesPlayed >= 100),
        Achievement("high_roller", "High Roller", "Wager 100,000 VC", 300, progress.totalWagered >= 100_000),
        Achievement("ten_streak", "Hot Hand", "Reach a 10 game win streak", 300, progress.winStreak >= 10)
    )

    fun missions(progress: UserProgress): List<DailyMission> = listOf(
        DailyMission("play5", "Play 5 rounds", progress.dailyGames, 5, 700, 75),
        DailyMission("win3", "Win 3 rounds", progress.dailyWins, 3, 900, 100),
        DailyMission("cases2", "Open 2 cases", progress.dailyCases, 2, 1_000, 100),
        DailyMission("bets5", "Place 5 virtual bets", progress.dailyBets, 5, 1_000, 100),
        DailyMission("variety3", "Play 3 different games", progress.dailyDistinctGames.size, 3, 1_200, 125)
    )

    suspend fun reset() = context.progressionDataStore.edit { it.clear() }

    private suspend fun incrementDaily(key: androidx.datastore.preferences.core.Preferences.Key<Int>) {
        val today = LocalDate.now().toEpochDay()
        context.progressionDataStore.edit { p ->
            resetDailyIfNeeded(p, today)
            p[key] = (p[key] ?: 0) + 1
        }
    }

    private fun resetDailyIfNeeded(p: androidx.datastore.preferences.core.MutablePreferences, today: Long) {
        if (p[Keys.dailyCountersEpochDay] == today) return
        p[Keys.dailyCountersEpochDay] = today
        p[Keys.dailyGames] = 0
        p[Keys.dailyWins] = 0
        p[Keys.dailyCases] = 0
        p[Keys.dailyBets] = 0
        p[Keys.dailyDistinctGames] = ""
    }

    private fun decodeCounts(value: String): Map<String, Int> = value.split(';').mapNotNull { token ->
        val split = token.lastIndexOf('=')
        if (split <= 0) null else token.substring(0, split) to (token.substring(split + 1).toIntOrNull() ?: 0)
    }.toMap()

    private fun encodeCounts(value: Map<String, Int>) = value.entries.joinToString(";") { "${it.key}=${it.value}" }
    private fun decodeSet(value: String): Set<String> = value.split('|').filter { it.isNotBlank() }.toSet()
    private fun encodeSet(value: Set<String>) = value.joinToString("|")
}
