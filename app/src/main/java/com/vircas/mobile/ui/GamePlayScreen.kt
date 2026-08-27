package com.vircas.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.AppContainer
import com.vircas.mobile.core.data.InventoryItemEntity
import com.vircas.mobile.core.random.SeededRandomProvider
import com.vircas.mobile.core.wallet.WalletRepository
import com.vircas.mobile.game.engines.*
import java.util.UUID
import kotlinx.coroutines.launch

private const val DEFAULT_STAKE = 500L

private data class InstantRoundResult(
    val label: String,
    val payout: Long,
    val multiplier: Double,
    val details: String = ""
)

@Composable
fun GamePlayScreen(gameId: String, container: AppContainer, onBack: () -> Unit) {
    val balance by container.walletRepository.balance.collectAsState(initial = WalletRepository.STARTING_BALANCE)
    var lastResult by remember(gameId) { mutableStateOf("Choose Play to start a virtual round.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = gameTitle(gameId)

    fun runRound() {
        if (busy) return
        busy = true
        scope.launch {
            if (!container.walletRepository.debit(DEFAULT_STAKE)) {
                lastResult = "Not enough VC for a $DEFAULT_STAKE VC stake."
                busy = false
                return@launch
            }
            val seed = UUID.randomUUID().toString().replace("-", "")
            val result = playInstant(gameId, seed)
            if (result.payout > 0) container.walletRepository.credit(result.payout)
            container.historyRepository.record(
                game = title,
                stake = DEFAULT_STAKE,
                payout = result.payout,
                multiplier = result.multiplier,
                result = result.label,
                details = result.details
            )
            container.progressionRepository.recordGame(title, DEFAULT_STAKE, result.payout)
            container.fairnessRepository.record(title, seed, "local", result.label)
            lastResult = result.label
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(gameSubtitle(gameId), color = Color(0xFF94A3B8))
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF111827)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("BALANCE", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text("$balance VC", fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2D))
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Round result", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text(lastResult, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Stake: $DEFAULT_STAKE VC · all outcomes are local and virtual", color = Color(0xFF94A3B8))
            }
        }

        Button(onClick = ::runRound, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (busy) "Resolving…" else "Play $DEFAULT_STAKE VC", fontWeight = FontWeight.Bold)
        }

        if (gameId in setOf("mines", "ladder", "towers", "hilo", "crash")) {
            Text(
                "This screen resolves one complete quick round through the same engine used by the game model. Dedicated animated multi-step presentation can build on the engine without moving outcome logic into UI.",
                color = Color(0xFF64748B),
                fontSize = 12.sp
            )
        }
    }
}

private fun playInstant(gameId: String, seed: String): InstantRoundResult {
    val random = SeededRandomProvider(seed.hashCode().toLong())
    return when (gameId) {
        "dice" -> {
            val r = DiceEngine(random).roll(60.0, under = true)
            val m = r.outcome.multiplier
            InstantRoundResult("Rolled ${"%.2f".format(r.roll)} · ${if (m > 0) "WIN ${"%.2f".format(m)}x" else "LOSS"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "coinflip" -> {
            val r = CoinflipEngine(random).flip(CoinflipEngine.Side.HEADS)
            val m = r.outcome.multiplier
            InstantRoundResult("${r.side.name} · ${if (m > 0) "WIN" else "LOSS"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "wheel" -> {
            val r = WheelEngine(random).spin()
            val m = r.multiplier
            InstantRoundResult("Wheel stopped at ${if (m > 0) "${m}x" else "0x"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "roulette" -> {
            val r = RouletteEngine(random).spin(RouletteBet.Color(RouletteColor.RED))
            val m = r.payoutMultiplier
            InstantRoundResult("${r.number} ${r.color.name} · ${if (r.won) "WIN" else "LOSS"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "blackjack" -> {
            val engine = BlackjackEngine(random)
            var round = engine.newRound()
            if (round.status == BlackjackStatus.PLAYER_TURN) round = engine.stand(round)
            val m = round.payoutMultiplier
            val player = BlackjackEngine.score(round.player).total
            val dealer = BlackjackEngine.score(round.dealer).total
            InstantRoundResult("${round.status.name.replace('_', ' ')} · $player vs $dealer", (DEFAULT_STAKE * m).toLong(), m)
        }
        "hilo" -> {
            val engine = HiLoEngine(random)
            val start = engine.newRound()
            val r = engine.guess(start, HiLoGuess.HIGHER)
            val m = r.payoutMultiplier
            InstantRoundResult("${start.current.rank.name} → ${r.next.rank.name} · ${if (r.won) "WIN" else "LOSS"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "towers" -> {
            val engine = TowersEngine(random)
            val r = engine.choose(engine.newRound(), 0)
            val m = r.payoutMultiplier
            InstantRoundResult("Tower floor 1 · ${if (r.won) "SAFE ${"%.2f".format(m)}x" else "TRAP"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "ladder" -> {
            val engine = LadderEngine(random)
            val r = engine.choose(engine.newRound(), 0)
            val m = r.payoutMultiplier
            InstantRoundResult("Ladder step 1 · ${if (r.won) "SAFE ${m}x" else "MISS"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "mines" -> {
            val engine = MinesEngine(random)
            val round = engine.newRound(3)
            val (_, outcome) = engine.reveal(round, 0)
            val m = outcome?.multiplier ?: 0.0
            InstantRoundResult("Tile 1 · ${if (m > 0) "SAFE ${"%.2f".format(m)}x" else "MINE"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "slots" -> {
            val r = SlotsEngine(random).spin(SlotsEngine.NeonFruits)
            val m = r.payoutMultiplier
            val grid = r.grid.joinToString(" / ") { row -> row.joinToString(" ") { it.id } }
            InstantRoundResult("${if (m > 0) "WIN ${"%.2f".format(m)}x" else "No win"}", (DEFAULT_STAKE * m).toLong(), m, grid)
        }
        "crash" -> {
            val engine = CrashEngine(random)
            val round = engine.newRound()
            val cash = engine.cashOut(round, 2.0)
            val m = cash.payoutMultiplier
            InstantRoundResult("Crash ${round.crashPoint}x · auto collect 2.00x ${if (cash.won) "WON" else "MISSED"}", (DEFAULT_STAKE * m).toLong(), m)
        }
        "plinko" -> {
            val r = PlinkoEngine(random).drop(PlinkoRisk.MEDIUM)
            InstantRoundResult("Bucket ${r.bucket} · ${r.multiplier}x", (DEFAULT_STAKE * r.multiplier).toLong(), r.multiplier)
        }
        "horse" -> {
            val engine = HorseRacingEngine(random)
            val race = engine.generateRace(count = 8)
            val pick = race.horses.first()
            val result = engine.simulate(race)
            val won = result.winnerId == pick.id
            val m = if (won) pick.odds else 0.0
            InstantRoundResult("Picked ${pick.name} · winner ${race.horses.first { it.id == result.winnerId }.name}", (DEFAULT_STAKE * m).toLong(), m, result.finishOrder.joinToString())
        }
        else -> InstantRoundResult("Game engine unavailable", 0, 0.0)
    }
}

fun gameTitle(id: String): String = when (id) {
    "dice" -> "Dice"
    "coinflip" -> "Coinflip"
    "mines" -> "Mines"
    "wheel" -> "Wheel"
    "roulette" -> "Roulette"
    "blackjack" -> "Blackjack"
    "hilo" -> "Hi-Lo"
    "towers" -> "Towers"
    "ladder" -> "Ladder"
    "slots" -> "Slots"
    "crash" -> "Crash"
    "plinko" -> "Plinko"
    "cases" -> "Cases"
    "horse" -> "Horse Racing"
    else -> id.replaceFirstChar { it.uppercase() }
}

private fun gameSubtitle(id: String): String = when (id) {
    "roulette" -> "European 0–36 roulette"
    "blackjack" -> "Dealer stands on 17 · blackjack pays 3:2"
    "slots" -> "Neon Fruits quick spin"
    "horse" -> "Fictional locally simulated race"
    else -> "Offline virtual game · no real money"
}
