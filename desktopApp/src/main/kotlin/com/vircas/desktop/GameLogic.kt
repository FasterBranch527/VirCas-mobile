package com.vircas.desktop

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class DesktopGame(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String
)

val desktopGames = listOf(
    DesktopGame("roulette", "Roulette", "European 0–36", "◉"),
    DesktopGame("blackjack", "Blackjack", "Classic 21", "♠"),
    DesktopGame("mines", "Mines", "Pick a safe tile", "✦"),
    DesktopGame("crash", "Crash", "Ride the multiplier", "↗"),
    DesktopGame("slots", "Slots", "Three-reel spin", "▦"),
    DesktopGame("plinko", "Plinko", "Drop through the board", "●"),
    DesktopGame("dice", "Dice", "Roll under 50", "⚄"),
    DesktopGame("coinflip", "Coin Flip", "Heads or tails", "◐"),
    DesktopGame("wheel", "Wheel", "Spin for multiplier", "◎"),
    DesktopGame("hilo", "Hi-Lo", "Higher or lower", "↕"),
    DesktopGame("towers", "Towers", "Climb for payout", "▥"),
    DesktopGame("ladder", "Ladder", "Step-by-step risk", "⌁"),
    DesktopGame("horse", "Horse Racing", "Local animated race", "♞")
)

fun resolveDesktopGame(id: String, random: Random): Pair<Double, String> = when (id) {
    "roulette" -> {
        val n = random.nextInt(37)
        if (n == 0) 35.0 to "0 — jackpot pocket"
        else if (n % 2 == 0) 2.0 to "${n} — even wins"
        else 0.0 to "${n} — odd"
    }
    "blackjack" -> {
        val player = hand(random)
        val dealer = hand(random)
        when {
            player > 21 -> 0.0 to "Player busts ${player} · dealer ${dealer}"
            dealer > 21 -> 2.0 to "Dealer busts ${dealer} · player ${player}"
            player > dealer -> 2.0 to "${player} beats ${dealer}"
            player == dealer -> 1.0 to "Push ${player} : ${dealer}"
            else -> 0.0 to "${player} loses to ${dealer}"
        }
    }
    "mines" -> if (random.nextInt(5) != 0) 1.22 to "Safe tile" else 0.0 to "Mine"
    "crash" -> {
        val x = min(25.0, max(1.0, 0.99 / max(0.02, 1.0 - random.nextDouble())))
        val cashout = 1.75
        if (x >= cashout) cashout to "Cashed at ${"%.2f".format(cashout)}× · crash ${"%.2f".format(x)}×"
        else 0.0 to "Crashed at ${"%.2f".format(x)}×"
    }
    "slots" -> {
        val symbols = listOf("7", "BAR", "◆", "★", "●")
        val a = symbols.random(random)
        val b = symbols.random(random)
        val c = symbols.random(random)
        when {
            a == b && b == c -> 8.0 to "${a} ${b} ${c}"
            a == b || b == c || a == c -> 2.0 to "${a} ${b} ${c}"
            else -> 0.0 to "${a} ${b} ${c}"
        }
    }
    "plinko" -> {
        val slots = listOf(0.4, 0.8, 1.1, 1.5, 3.0, 1.5, 1.1, 0.8, 0.4)
        val index = random.nextInt(slots.size)
        slots[index] to "Landed slot ${index + 1} · ${slots[index]}×"
    }
    "dice" -> {
        val roll = random.nextInt(1, 101)
        if (roll < 50) 2.0 to "Rolled ${roll} — under 50" else 0.0 to "Rolled ${roll} — over"
    }
    "coinflip" -> if (random.nextBoolean()) 2.0 to "Heads" else 0.0 to "Tails"
    "wheel" -> {
        val values = listOf(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0, 25.0)
        val value = values.random(random)
        value to "Wheel landed on ${value}×"
    }
    "hilo" -> {
        val first = random.nextInt(2, 15)
        val next = random.nextInt(2, 15)
        if (next >= first) 1.85 to "${first} → ${next} · higher" else 0.0 to "${first} → ${next} · lower"
    }
    "towers" -> {
        val floors = random.nextInt(1, 6)
        val win = random.nextDouble() > floors * 0.08
        if (win) (1.0 + floors * 0.45) to "Cleared ${floors} floor(s)" else 0.0 to "Missed on floor ${floors}"
    }
    "ladder" -> {
        val steps = random.nextInt(1, 7)
        val win = random.nextDouble() > steps * 0.07
        if (win) (1.0 + steps * 0.35) to "Reached step ${steps}" else 0.0 to "Fell at step ${steps}"
    }
    "horse" -> {
        val horse = random.nextInt(1, 7)
        if (horse == 3) 5.5 to "Horse #3 wins" else 0.0 to "Horse #${horse} wins"
    }
    else -> 1.0 to "No-op"
}

private fun hand(random: Random): Int {
    var total = random.nextInt(4, 12) + random.nextInt(4, 12)
    while (total < 17 && random.nextBoolean()) total += random.nextInt(2, 11)
    return total
}
