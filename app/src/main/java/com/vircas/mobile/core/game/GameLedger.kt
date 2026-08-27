package com.vircas.mobile.core.game

import com.vircas.mobile.core.data.FairnessRepository
import com.vircas.mobile.core.data.GameHistoryRepository
import com.vircas.mobile.core.progression.ProgressionRepository
import com.vircas.mobile.core.wallet.WalletRepository
import java.security.SecureRandom
import java.util.UUID

data class ActiveWager(val id: String, val game: String, val stake: Long, val startedAt: Long)

data class RoundReceipt(
    val wagerId: String,
    val roundId: String,
    val game: String,
    val stake: Long,
    val payout: Long,
    val multiplier: Double,
    val result: String
) {
    val profitLoss: Long get() = payout - stake
}

class GameLedger(
    private val wallet: WalletRepository,
    private val history: GameHistoryRepository,
    private val progression: ProgressionRepository,
    private val fairness: FairnessRepository
) {
    private val secureRandom = SecureRandom()

    suspend fun begin(game: String, stake: Long): ActiveWager? {
        if (game.isBlank() || stake <= 0L) return null
        if (!wallet.debit(stake)) return null
        return ActiveWager(UUID.randomUUID().toString(), game, stake, System.currentTimeMillis())
    }

    suspend fun settle(
        wager: ActiveWager,
        multiplier: Double,
        result: String,
        details: String = "",
        generatedSeed: String = randomSeed(),
        clientSeed: String = "local"
    ): RoundReceipt {
        require(multiplier >= 0.0 && multiplier.isFinite())
        val payout = safePayout(wager.stake, multiplier)
        if (payout > 0L) wallet.credit(payout)
        history.record(wager.game, wager.stake, payout, multiplier, result, details)
        progression.recordGame(wager.game, wager.stake, payout)
        val roundId = fairness.record(wager.game, generatedSeed, clientSeed, result)
        return RoundReceipt(wager.id, roundId, wager.game, wager.stake, payout, multiplier, result)
    }

    suspend fun cancel(wager: ActiveWager) {
        wallet.credit(wager.stake)
    }

    private fun safePayout(stake: Long, multiplier: Double): Long {
        val value = stake.toDouble() * multiplier
        return when {
            !value.isFinite() || value >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
            value <= 0.0 -> 0L
            else -> value.toLong()
        }
    }

    private fun randomSeed(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
