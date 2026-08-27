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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.random.RandomProvider
import com.vircas.mobile.game.engines.*

private const val DEFAULT_STAKE = 500L

@Composable
fun GamePlayScreen(gameId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var lastResult by remember(gameId) { mutableStateOf("Choose Play to start a virtual round.") }
    var busy by remember { mutableStateOf(false) }
    val title = gameTitle(gameId)

    fun runRound() {
        if (busy) return
        busy = true
        viewModel.playResolved(
            game = title,
            stake = DEFAULT_STAKE,
            resolver = { random -> playInstant(gameId, random) }
        ) { receipt ->
            lastResult = receipt?.let {
                "${it.result} · ${if (it.payout > 0) "payout ${it.payout} VC" else "no payout"}"
            } ?: "Round could not start. Check your balance."
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
                "Quick play settles a complete short round through the same domain engine. Multi-step wager APIs live in the ViewModel for dedicated animated presentations.",
                color = Color(0xFF64748B),
                fontSize = 12.sp
            )
        }
    }
}

private fun playInstant(gameId: String, random: RandomProvider): ResolvedPlay = when (gameId) {
    "dice" -> {
        val r = DiceEngine(random).roll(60.0, under = true)
        val m = r.outcome.multiplier
        ResolvedPlay(m, "Rolled ${"%.2f".format(r.roll)} · ${if (m > 0) "WIN ${"%.2f".format(m)}x" else "LOSS"}")
    }
    "coinflip" -> {
        val r = CoinflipEngine(random).flip(CoinflipEngine.Side.HEADS)
        ResolvedPlay(r.outcome.multiplier, "${r.side.name} · ${if (r.outcome.multiplier > 0) "WIN" else "LOSS"}")
    }
    "wheel" -> {
        val r = WheelEngine(random).spin()
        ResolvedPlay(r.multiplier, "Wheel stopped at ${r.multiplier}x")
    }
    "roulette" -> {
        val r = RouletteEngine(random).spin(RouletteBet.Color(RouletteColor.RED))
        ResolvedPlay(r.payoutMultiplier, "${r.number} ${r.color.name} · ${if (r.won) "WIN" else "LOSS"}")
    }
    "blackjack" -> {
        val engine = BlackjackEngine(random)
        var round = engine.newRound()
        if (round.status == BlackjackStatus.PLAYER_TURN) round = engine.stand(round)
        val player = BlackjackEngine.score(round.player).total
        val dealer = BlackjackEngine.score(round.dealer).total
        ResolvedPlay(round.payoutMultiplier, "${round.status.name.replace('_', ' ')} · $player vs $dealer")
    }
    "hilo" -> {
        val engine = HiLoEngine(random)
        val start = engine.newRound()
        val r = engine.guess(start, HiLoGuess.HIGHER)
        ResolvedPlay(r.payoutMultiplier, "${start.current.rank.name} → ${r.next.rank.name} · ${if (r.won) "WIN" else "LOSS"}")
    }
    "towers" -> {
        val engine = TowersEngine(random)
        val r = engine.choose(engine.newRound(), 0)
        ResolvedPlay(r.payoutMultiplier, "Tower floor 1 · ${if (r.won) "SAFE ${"%.2f".format(r.payoutMultiplier)}x" else "TRAP"}")
    }
    "ladder" -> {
        val engine = LadderEngine(random)
        val r = engine.choose(engine.newRound(), 0)
        ResolvedPlay(r.payoutMultiplier, "Ladder step 1 · ${if (r.won) "SAFE ${r.payoutMultiplier}x" else "MISS"}")
    }
    "mines" -> {
        val engine = MinesEngine(random)
        val (_, outcome) = engine.reveal(engine.newRound(3), 0)
        val m = outcome?.multiplier ?: 0.0
        ResolvedPlay(m, "Tile 1 · ${if (m > 0) "SAFE ${"%.2f".format(m)}x" else "MINE"}")
    }
    "slots" -> {
        val r = SlotsEngine(random).spin(SlotsEngine.NeonFruits)
        val grid = r.grid.joinToString(" / ") { row -> row.joinToString(" ") { it.id } }
        ResolvedPlay(r.payoutMultiplier, if (r.payoutMultiplier > 0) "WIN ${"%.2f".format(r.payoutMultiplier)}x" else "No win", grid)
    }
    "crash" -> {
        val engine = CrashEngine(random)
        val round = engine.newRound()
        val cash = engine.cashOut(round, 2.0)
        ResolvedPlay(cash.payoutMultiplier, "Crash ${round.crashPoint}x · auto collect 2.00x ${if (cash.won) "WON" else "MISSED"}")
    }
    "plinko" -> {
        val r = PlinkoEngine(random).drop(PlinkoRisk.MEDIUM)
        ResolvedPlay(r.multiplier, "Bucket ${r.bucket} · ${r.multiplier}x", r.path.joinToString("") { if (it) "R" else "L" })
    }
    "horse" -> {
        val engine = HorseRacingEngine(random)
        val race = engine.generateRace(count = 8)
        val pick = race.horses.first()
        val result = engine.simulate(race)
        val won = result.winnerId == pick.id
        val m = if (won) pick.odds else 0.0
        ResolvedPlay(m, "Picked ${pick.name} · winner ${race.horses.first { it.id == result.winnerId }.name}", result.finishOrder.joinToString())
    }
    else -> ResolvedPlay(0.0, "Game engine unavailable")
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
