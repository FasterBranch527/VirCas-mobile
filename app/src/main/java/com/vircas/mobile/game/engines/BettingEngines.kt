package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider
import kotlin.math.round

enum class VirtualSport { FOOTBALL, BASKETBALL, TENNIS, HOCKEY, ESPORTS }

data class VirtualEvent(
    val id: String,
    val sport: VirtualSport,
    val home: String,
    val away: String,
    val homeOdds: Double,
    val drawOdds: Double?,
    val awayOdds: Double,
    val startsAt: Long
)

data class MarketSelection(val id: String, val eventId: String, val label: String, val odds: Double)
data class LiveMoment(val minute: Int, val text: String)
data class SimulatedEventResult(
    val eventId: String,
    val homeScore: Int,
    val awayScore: Int,
    val winnerSelectionId: String,
    val liveMoments: List<LiveMoment>
)

class SportsBettingEngine(private val random: RandomProvider) {
    fun generateEvents(now: Long = System.currentTimeMillis()): List<VirtualEvent> {
        val pools = mapOf(
            VirtualSport.FOOTBALL to listOf("Neon City", "Iron Vale", "Northstar FC", "Crimson Harbor", "Orion Athletic", "Silverline"),
            VirtualSport.BASKETBALL to listOf("Metro Pulse", "Solar Giants", "Arctic Hoops", "Nova Comets", "Titan Five", "Vector Storm"),
            VirtualSport.TENNIS to listOf("Mika Voss", "Leon Ardent", "Iris Kade", "Nora Vale", "Jace Orion", "Sena Frost"),
            VirtualSport.HOCKEY to listOf("Polar Kings", "Redforge", "Glacier Unit", "Night Blades", "Aurora Ice", "Steel Wolves")
        )
        return pools.flatMap { (sport, names) ->
            names.chunked(2).mapIndexedNotNull { index, pair ->
                if (pair.size < 2) null else createEvent(
                    id = "${sport.name.lowercase()}_$index",
                    sport = sport,
                    home = pair[0],
                    away = pair[1],
                    startsAt = now + (index + 1) * 900_000L
                )
            }
        }
    }

    fun selections(event: VirtualEvent): List<MarketSelection> = buildList {
        add(MarketSelection("${event.id}:home", event.id, event.home, event.homeOdds))
        event.drawOdds?.let { add(MarketSelection("${event.id}:draw", event.id, "Draw", it)) }
        add(MarketSelection("${event.id}:away", event.id, event.away, event.awayOdds))
    }

    fun simulate(event: VirtualEvent): SimulatedEventResult {
        val homeWeight = 1.0 / event.homeOdds
        val awayWeight = 1.0 / event.awayOdds
        val drawWeight = event.drawOdds?.let { 1.0 / it } ?: 0.0
        val total = homeWeight + awayWeight + drawWeight
        val roll = random.nextDouble() * total
        val winner = when {
            roll < homeWeight -> "${event.id}:home"
            roll < homeWeight + drawWeight -> "${event.id}:draw"
            else -> "${event.id}:away"
        }
        val basketball = event.sport == VirtualSport.BASKETBALL
        var homeScore = if (basketball) 70 + random.nextInt(0, 35) else random.nextInt(0, 4)
        var awayScore = if (basketball) 70 + random.nextInt(0, 35) else random.nextInt(0, 4)
        when {
            winner.endsWith(":home") && homeScore <= awayScore -> homeScore = awayScore + 1
            winner.endsWith(":away") && awayScore <= homeScore -> awayScore = homeScore + 1
            winner.endsWith(":draw") -> awayScore = homeScore
        }
        val moments = buildList {
            if (homeScore > 0) add(LiveMoment(24, "${event.home} score"))
            if (awayScore > 0) add(LiveMoment(61, "${event.away} answer"))
            add(LiveMoment(90, "Simulation complete"))
        }
        return SimulatedEventResult(event.id, homeScore, awayScore, winner, moments)
    }

    private fun createEvent(id: String, sport: VirtualSport, home: String, away: String, startsAt: Long): VirtualEvent {
        val homePower = 0.75 + random.nextDouble() * 0.5
        val awayPower = 0.75 + random.nextDouble() * 0.5
        val drawWeight = if (sport == VirtualSport.FOOTBALL || sport == VirtualSport.HOCKEY) 0.24 else 0.0
        val sum = homePower + awayPower + drawWeight
        val margin = 0.94
        return VirtualEvent(
            id = id,
            sport = sport,
            home = home,
            away = away,
            homeOdds = round2(sum / homePower / margin),
            drawOdds = if (drawWeight > 0.0) round2(sum / drawWeight / margin) else null,
            awayOdds = round2(sum / awayPower / margin),
            startsAt = startsAt
        )
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0
}

data class EsportsEvent(
    val id: String,
    val discipline: String,
    val teamA: String,
    val teamB: String,
    val matchWinnerA: Double,
    val matchWinnerB: Double,
    val mapHandicapA: Double,
    val totalMapsOver: Double,
    val mapWinnerA: Double = matchWinnerA,
    val mapWinnerB: Double = matchWinnerB,
    val totalMapsUnder: Double = 2.0,
    val mapHandicapB: Double = 1.65
)

data class EsportsSimulationResult(
    val eventId: String,
    val mapsA: Int,
    val mapsB: Int,
    val mapOneWinner: String,
    val winningSelectionIds: Set<String>
)

class EsportsBettingEngine(private val random: RandomProvider) {
    private val teams = listOf("Nova Five", "Arctic Wolves", "Neon Core", "Titan Squad", "ZeroPoint", "Orbit Nine")
    private val games = listOf("CS2-like", "MOBA-like", "Tactical Shooter", "Battle Royale")

    fun generate(): List<EsportsEvent> = teams.chunked(2).mapIndexedNotNull { index, pair ->
        if (pair.size < 2) null else {
            val a = 0.8 + random.nextDouble() * 0.5
            val b = 0.8 + random.nextDouble() * 0.5
            val sum = a + b
            val matchA = round2(sum / a / 0.95)
            val matchB = round2(sum / b / 0.95)
            EsportsEvent(
                id = "esports_$index",
                discipline = games[index % games.size],
                teamA = pair[0],
                teamB = pair[1],
                matchWinnerA = matchA,
                matchWinnerB = matchB,
                mapHandicapA = round2(1.7 + random.nextDouble()),
                totalMapsOver = round2(1.6 + random.nextDouble()),
                mapWinnerA = round2((matchA * 0.92).coerceAtLeast(1.2)),
                mapWinnerB = round2((matchB * 0.92).coerceAtLeast(1.2)),
                totalMapsUnder = round2(1.65 + random.nextDouble() * 0.8),
                mapHandicapB = round2(1.7 + random.nextDouble())
            )
        }
    }

    fun selections(event: EsportsEvent): List<MarketSelection> = listOf(
        MarketSelection("${event.id}:match:a", event.id, "Match · ${event.teamA}", event.matchWinnerA),
        MarketSelection("${event.id}:match:b", event.id, "Match · ${event.teamB}", event.matchWinnerB),
        MarketSelection("${event.id}:map1:a", event.id, "Map 1 · ${event.teamA}", event.mapWinnerA),
        MarketSelection("${event.id}:map1:b", event.id, "Map 1 · ${event.teamB}", event.mapWinnerB),
        MarketSelection("${event.id}:maps:over", event.id, "Total maps Over 2.5", event.totalMapsOver),
        MarketSelection("${event.id}:maps:under", event.id, "Total maps Under 2.5", event.totalMapsUnder),
        MarketSelection("${event.id}:handicap:a", event.id, "${event.teamA} -1.5 maps", event.mapHandicapA),
        MarketSelection("${event.id}:handicap:b", event.id, "${event.teamB} +1.5 maps", event.mapHandicapB)
    )

    fun simulate(event: EsportsEvent): EsportsSimulationResult {
        val aWeight = 1.0 / event.matchWinnerA
        val bWeight = 1.0 / event.matchWinnerB
        val aWinsMatch = random.nextDouble() < aWeight / (aWeight + bWeight)
        val sweep = random.nextDouble() < 0.45
        val mapsA = when {
            aWinsMatch -> 2
            sweep -> 0
            else -> 1
        }
        val mapsB = when {
            !aWinsMatch -> 2
            sweep -> 0
            else -> 1
        }
        val mapOneA = random.nextDouble() < aWeight / (aWeight + bWeight)
        val totalMaps = mapsA + mapsB
        val winning = buildSet {
            add("${event.id}:match:${if (aWinsMatch) "a" else "b"}")
            add("${event.id}:map1:${if (mapOneA) "a" else "b"}")
            add("${event.id}:maps:${if (totalMaps == 3) "over" else "under"}")
            if (mapsA - mapsB >= 2) add("${event.id}:handicap:a") else add("${event.id}:handicap:b")
        }
        return EsportsSimulationResult(
            eventId = event.id,
            mapsA = mapsA,
            mapsB = mapsB,
            mapOneWinner = if (mapOneA) event.teamA else event.teamB,
            winningSelectionIds = winning
        )
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0
}

data class BetSlip(val selections: List<MarketSelection>, val stake: Long) {
    val combinedOdds: Double get() = selections.fold(1.0) { acc, selection -> acc * selection.odds }
    val possiblePayout: Long get() = (stake * combinedOdds).toLong()
}

class BetSlipEngine {
    fun create(selections: List<MarketSelection>, stake: Long): BetSlip {
        require(selections.isNotEmpty())
        require(stake > 0)
        require(selections.map { it.eventId }.distinct().size == selections.size)
        return BetSlip(selections, stake)
    }

    fun settle(slip: BetSlip, winners: Set<String>): Long =
        if (slip.selections.all { it.id in winners }) slip.possiblePayout else 0L
}

data class Horse(
    val id: String,
    val name: String,
    val odds: Double,
    val rating: Int,
    val form: Int,
    val speed: Int,
    val stamina: Int
)

data class HorseRace(val id: String, val horses: List<Horse>)
data class HorseRaceResult(val raceId: String, val finishOrder: List<String>) {
    val winnerId: String get() = finishOrder.first()
}

class HorseRacingEngine(private val random: RandomProvider) {
    private val names = listOf("Solar Echo", "Velvet Comet", "Iron Halo", "Night Circuit", "Aurora Jet", "Crimson Pace", "Silver Voltage", "Nova Run", "Glass Thunder", "Polar Flame")

    fun generateRace(id: String = "race_1", count: Int = 8): HorseRace {
        require(count in 6..10)
        val horses = names.take(count).mapIndexed { index, name ->
            val rating = 62 + random.nextInt(0, 38)
            val form = 55 + random.nextInt(0, 45)
            val speed = 58 + random.nextInt(0, 42)
            val stamina = 58 + random.nextInt(0, 42)
            val power = rating * 0.30 + form * 0.20 + speed * 0.30 + stamina * 0.20
            Horse("horse_$index", name, round2((115.0 / power).coerceIn(1.25, 12.0)), rating, form, speed, stamina)
        }
        return HorseRace(id, horses)
    }

    fun selections(race: HorseRace): List<MarketSelection> = race.horses.map { horse ->
        MarketSelection("${race.id}:horse:${horse.id}", race.id, horse.name, horse.odds)
    }

    fun simulate(race: HorseRace): HorseRaceResult {
        val remaining = race.horses.toMutableList()
        val order = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val scores = remaining.map { horse ->
                val base = horse.rating * 0.30 + horse.form * 0.20 + horse.speed * 0.30 + horse.stamina * 0.20
                base * (0.90 + random.nextDouble() * 0.20)
            }
            val best = scores.indices.maxByOrNull { scores[it] } ?: 0
            order += remaining.removeAt(best).id
        }
        return HorseRaceResult(race.id, order)
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0
}
