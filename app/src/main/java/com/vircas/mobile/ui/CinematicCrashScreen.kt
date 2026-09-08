package com.vircas.mobile.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.game.WagerRules
import com.vircas.mobile.game.engines.CrashEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive

@Composable
fun CinematicCrashGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val controller = rememberRound(viewModel, "crash-rocket") { createCrashController(viewModel) }
    val state by controller.state.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val ready by viewModel.ready.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val goBack by rememberUpdatedState(onBack)
    val frameTime = rememberCrashFrameTime(controller, state)
    val reducedMotion = settings.reducedMotion || !settings.animations
    val keyboard = LocalSoftwareKeyboardController.current
    val sceneBounds = remember(controller) { mutableStateOf<Rect?>(null) }
    val reportSceneBounds: (Rect) -> Unit = remember(sceneBounds) { { sceneBounds.value = it } }

    BackHandler { controller.requestLeave() }
    LaunchedEffect(controller, state.exitReady) {
        if (state.exitReady) {
            controller.consumeExit()
            goBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF070A10))) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0C1017), Color(0xFF070A10))))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
        ) {
            val landscape = maxWidth >= 600.dp && maxWidth > maxHeight
            if (landscape) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrashRocketHeader(balance, controller::requestLeave)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            CrashRecentFlights(state.recentCrashes)
                            CrashRocketScene(state, frameTime, reducedMotion, Modifier.weight(1f).fillMaxWidth(), reportSceneBounds)
                        }
                        CrashFlightControls(
                            state, balance, ready, frameTime,
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState(), enabled = state.blastFlight == null),
                            onStake = controller::changeStake,
                            onStart = { keyboard?.hide(); controller.start(balance) },
                            onCollect = { controller.collect() }
                        )
                    }
                }
            } else {
                // Keep the controls reachable on small screens / with the numeric keyboard open.
                val sceneHeight = (maxHeight - 400.dp).coerceAtLeast(200.dp)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = state.blastFlight == null).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CrashRocketHeader(balance, controller::requestLeave)
                    CrashRecentFlights(state.recentCrashes)
                    CrashRocketScene(state, frameTime, reducedMotion, Modifier.fillMaxWidth().height(sceneHeight), reportSceneBounds)
                    CrashFlightControls(
                        state, balance, ready, frameTime, Modifier.fillMaxWidth(),
                        onStake = controller::changeStake,
                        onStart = { keyboard?.hide(); controller.start(balance) },
                        onCollect = { controller.collect() }
                    )
                }
            }
        }
        CrashDestructionOverlay(state, frameTime, sceneBounds, reducedMotion, Modifier.matchParentSize())
    }
}

/** One frame clock. It drives drawing, never accumulates dt, and never owns a wager. */
@Composable
private fun rememberCrashFrameTime(controller: CrashRoundController, state: CrashUiState): State<Long> {
    val time = remember(controller) { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(controller, state.flight, state.blastFlight, state.phase, state.revealStartedAtNanos) {
        time.longValue = SystemClock.elapsedRealtimeNanos()
        if (state.flight == null && state.blastFlight == null) return@LaunchedEffect
        while (isActive) {
            // Do not mix the Choreographer frame timestamp's clock domain with elapsedRealtimeNanos.
            val now = withFrameNanos { SystemClock.elapsedRealtimeNanos() }
            time.longValue = now
            controller.tick(now)
        }
    }
    return time
}

private fun createCrashController(viewModel: AppViewModel): CrashRoundController = CrashRoundController(
    scope = viewModel.roundTaskScope("crash-rocket"),
    nowNanos = SystemClock::elapsedRealtimeNanos,
    port = object : CrashRoundPort {
        override fun reserve(stake: Long) = CompletableDeferred<ActiveWager?>().also { acknowledgement ->
            viewModel.beginWager("Crash", stake) { acknowledgement.complete(it) }
        }
        override fun newRound(wager: ActiveWager) = CrashEngine(viewModel.randomProvider(wager)).newRound()
        override fun checkpoint(wager: ActiveWager, multiplier: Double, result: String, details: String, terminal: Boolean) =
            viewModel.checkpointWager(wager, multiplier, result, details, terminal)
        override fun settle(wager: ActiveWager, multiplier: Double, result: String, details: String) =
            viewModel.settleWager(wager, multiplier, result, details)
        override fun cancel(wager: ActiveWager) = viewModel.cancelWager(wager)
    }
)

@Composable
private fun CrashRocketHeader(balance: Long, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("crash-back")) {
            Icon(Icons.Rounded.ArrowBack, "Back; cash out an active flight", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text("CRASH", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("FLIGHT CONTROL", color = CrashMuted, fontSize = 8.sp, letterSpacing = 1.4.sp)
        }
        Surface(shape = RoundedCornerShape(15.dp), color = CrashPanel, border = BorderStroke(1.dp, Color.White.copy(alpha = .06f))) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                Text("BALANCE", color = CrashMuted, fontSize = 7.sp, letterSpacing = 1.sp)
                Text(formatShellVc(balance), color = CrashMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CrashRecentFlights(values: List<Double>) {
    Row(Modifier.fillMaxWidth().height(30.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("RECENT", color = CrashMuted, fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.padding(end = 3.dp))
        if (values.isEmpty()) {
            Text("Your flight history will appear here", color = CrashMuted.copy(alpha = .60f), fontSize = 10.sp)
        }
        values.forEach { multiplier ->
            val color = if (multiplier >= 2.0) CrashMint else CrashMuted
            Surface(shape = RoundedCornerShape(9.dp), color = color.copy(alpha = .08f), border = BorderStroke(1.dp, color.copy(alpha = .13f))) {
                Text(crashMultiplierText(multiplier), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun CrashFlightControls(
    state: CrashUiState,
    balance: Long,
    ready: Boolean,
    frameTime: State<Long>,
    modifier: Modifier,
    onStake: (String) -> Unit,
    onStart: () -> Unit,
    onCollect: () -> Unit
) {
    Surface(modifier, shape = RoundedCornerShape(23.dp), color = CrashPanel, border = BorderStroke(1.dp, Color.White.copy(alpha = .07f))) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR STAKE", color = CrashMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, Modifier.size(11.dp), tint = CrashMint)
                    Spacer(Modifier.width(4.dp))
                    Text("PRECOMPUTED ROUND", color = CrashMint.copy(alpha = .7f), fontSize = 7.sp, letterSpacing = .6.sp)
                }
            }
            OutlinedTextField(
                value = state.stakeText, onValueChange = onStake, enabled = state.canEditStake,
                singleLine = true, suffix = { Text("VC", color = CrashMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().testTag("crash-stake"),
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    disabledTextColor = Color.White.copy(alpha = .6f),
                    focusedBorderColor = CrashMint, unfocusedBorderColor = Color.White.copy(alpha = .11f),
                    disabledBorderColor = Color.White.copy(alpha = .05f), cursorColor = CrashMint,
                    focusedContainerColor = CrashInk.copy(alpha = .35f), unfocusedContainerColor = CrashInk.copy(alpha = .35f)
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val stake = state.stakeText.toLongOrNull() ?: 0L
                CrashStakeChip("½", state.canEditStake, Modifier.weight(1f)) { onStake((stake / 2L).coerceAtLeast(1L).toString()) }
                listOf(500L, 1_000L, 5_000L).forEach { amount ->
                    CrashStakeChip(if (amount < 1000L) amount.toString() else "${amount / 1000L}K", state.canEditStake && amount <= balance, Modifier.weight(1f), state.stakeText == amount.toString()) { onStake(amount.toString()) }
                }
                CrashStakeChip("×2", state.canEditStake, Modifier.weight(1f)) {
                    onStake((stake.coerceAtMost(Long.MAX_VALUE / 2L) * 2L).coerceAtMost(balance).toString())
                }
            }
            when {
                state.leaving -> CrashAction("CLOSING ROUND…", "Waiting for saved result", false, {})
                state.settling -> CrashAction("SAVING RESULT…", "Please wait for confirmation", false, {})
                state.phase == CrashPhase.PREPARING -> CrashAction("PREPARING LAUNCH…", "Securing your virtual stake", false, {})
                state.phase == CrashPhase.CRASHED || state.phase == CrashPhase.RESETTING -> CrashAction("RESETTING FLIGHT…", "The next flight is not started automatically", false, {})
                state.phase == CrashPhase.FLYING && state.collectedAt != null -> {
                    Surface(Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(15.dp), color = CrashMint.copy(alpha = .09f), border = BorderStroke(1.dp, CrashMint.copy(alpha = .26f))) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("COLLECTED ${formatShellVc(state.payout)}", color = CrashMint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${crashMultiplierText(state.collectedAt)} · FLIGHT CONTINUES", color = CrashMuted, fontSize = 9.sp)
                        }
                    }
                }
                state.phase == CrashPhase.FLYING -> CrashCollectAction(state, frameTime, onCollect)
                else -> {
                    val stake = state.stakeText.toLongOrNull() ?: 0L
                    CrashAction(
                        if (state.phase == CrashPhase.CRASHED) "FLY AGAIN" else "LAUNCH ROCKET",
                        "${formatShellVc(stake)} · VIRTUAL STAKE",
                        ready && state.canEditStake && stake > 0L && stake <= balance,
                        onStart, "crash-launch"
                    )
                }
            }
            Text(state.message, color = CrashMuted, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 2)
        }
    }
}

@Composable
private fun CrashStakeChip(label: String, enabled: Boolean, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp), shape = RoundedCornerShape(10.dp),
        color = if (selected) CrashMint.copy(alpha = .10f) else Color.White.copy(alpha = .035f),
        border = BorderStroke(1.dp, if (selected) CrashMint.copy(alpha = .32f) else Color.White.copy(alpha = .045f))
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = if (!enabled) CrashMuted.copy(alpha = .38f) else if (selected) CrashMint else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CrashCollectAction(state: CrashUiState, frameTime: State<Long>, onCollect: () -> Unit) {
    val payout by remember(state.flight, state.stake, frameTime) {
        derivedStateOf {
            val multiplier = state.flight?.cashoutMultiplier(frameTime.value)
            if (multiplier == null) null else WagerRules.payout(state.stake, multiplier)
        }
    }
    CrashAction("CASH OUT", "${formatShellVc(payout ?: 0L)} · CURRENT PAYOUT", payout != null, onCollect, "crash-cashout")
}

@Composable
private fun CrashAction(label: String, detail: String, enabled: Boolean, onClick: () -> Unit, tag: String = "crash-wait") {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(62.dp).testTag(tag),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CrashMint, contentColor = CrashInk, disabledContainerColor = Color(0xFF233747), disabledContentColor = CrashMuted)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            Text(detail, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}
