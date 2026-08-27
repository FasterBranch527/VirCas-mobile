package com.vircas.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf(
        "Virtual Gaming Hub",
        "Everything is virtual",
        "No deposits. No withdrawals."
    )
    val bodies = listOf(
        "A polished offline collection of casino-style simulations, originals, cases and fictional betting.",
        "Coins, items, odds, teams, horses and events exist only inside VirCas and have no monetary value.",
        "There are no real-money purchases, cashout, cards, crypto or bookmaker/casino integrations."
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    )
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Casino,
                    contentDescription = null,
                    modifier = Modifier.padding(26.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "0${page + 1} / 03",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Text(titles[page], fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(bodies[page], color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { if (page < 2) page++ else onComplete() },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(if (page < 2) "CONTINUE" else "START PLAYING", fontWeight = FontWeight.Bold)
            }
        }
    }
}
