package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val accent: Color,
    val points: List<String>
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pages = remember {
        listOf(
            OnboardingPage(
                eyebrow = "WELCOME TO VIRCAS",
                title = "Your virtual casino floor",
                body = "Roulette, blackjack, originals, cases, racing and fictional betting in one local gaming hub.",
                icon = Icons.Rounded.Casino,
                accent = ShellPurple,
                points = listOf("15+ playable modes", "Offline-first", "Persistent history")
            ),
            OnboardingPage(
                eyebrow = "VIRTUAL WALLET",
                title = "Play with as much VC as you want",
                body = "VirCas coins have no monetary value. Add virtual funds whenever you want and use them across every game.",
                icon = Icons.Rounded.AccountBalanceWallet,
                accent = ShellGold,
                points = listOf("Starts with 100K VC", "Unlimited free top-ups", "No deposits or withdrawals")
            ),
            OnboardingPage(
                eyebrow = "LOCAL FAIRNESS",
                title = "Outcome first. Animation second.",
                body = "Rounds are resolved before the visual animation. Seeds and results are recorded locally so every session stays inspectable.",
                icon = Icons.Rounded.Shield,
                accent = ShellGreen,
                points = listOf("Recorded round seed", "Client seed support", "100% virtual simulator")
            )
        )
    }
    val current = pages[page]

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF070A10),
                    current.accent.copy(alpha = 0.12f),
                    Color(0xFF080B12)
                )
            )
        ).padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.06f)) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = ShellGold)
                        Spacer(Modifier.width(6.dp))
                        Text("VIRCAS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("0${page + 1} / 03", color = current.accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(148.dp),
                        shape = CircleShape,
                        color = current.accent.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, current.accent.copy(alpha = 0.18f))
                    ) {}
                    Surface(
                        modifier = Modifier.size(104.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = current.accent.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, current.accent.copy(alpha = 0.34f))
                    ) {
                        Icon(
                            current.icon,
                            null,
                            Modifier.padding(27.dp),
                            tint = current.accent
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.eyebrow, color = current.accent, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    Text(
                        current.title,
                        color = Color.White,
                        fontSize = 34.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        current.body,
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.points.forEach { point ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.045f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = current.accent.copy(alpha = 0.18f)) {
                                    Spacer(Modifier.size(9.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(point, color = Color.White.copy(alpha = 0.84f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    pages.indices.forEach { index ->
                        Surface(
                            modifier = Modifier.width(if (index == page) 28.dp else 8.dp).height(8.dp),
                            shape = CircleShape,
                            color = if (index == page) current.accent else Color.White.copy(alpha = 0.16f)
                        ) {}
                        if (index != pages.lastIndex) Spacer(Modifier.width(7.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (page > 0) {
                        OutlinedButton(
                            onClick = { page-- },
                            modifier = Modifier.weight(0.42f).height(54.dp)
                        ) { Text("BACK", fontWeight = FontWeight.Bold) }
                    }
                    Button(
                        onClick = { if (page < pages.lastIndex) page++ else onComplete() },
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) {
                        Text(if (page < pages.lastIndex) "CONTINUE" else "ENTER VIRCAS", fontWeight = FontWeight.Black)
                    }
                }
                Text(
                    "VirCas is a virtual-only simulator. VC, items and betting markets cannot be exchanged for real money.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
