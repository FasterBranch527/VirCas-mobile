package com.vircas.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Properties
import kotlin.math.roundToLong
import kotlin.random.Random

data class HistoryEntry(
    val game: String,
    val stake: Long,
    val payout: Long,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DesktopState {
    private val baseDir: Path = Path.of(System.getProperty("user.home"), ".vircas")
    private val settingsFile = baseDir.resolve("desktop.properties")
    private val historyFile = baseDir.resolve("history.tsv")
    private val secureRandom = SecureRandom()

    var balance by mutableStateOf(100_000L)
        private set
    var darkMode by mutableStateOf(true)
    var reducedMotion by mutableStateOf(false)
    var secureRng by mutableStateOf(true)
    var debugSeed by mutableStateOf(424242L)
    var clientSeed by mutableStateOf("vircas-desktop")
    var onboardingComplete by mutableStateOf(false)
    val history = mutableStateListOf<HistoryEntry>()

    init {
        Files.createDirectories(baseDir)
        load()
    }

    fun addFunds(amount: Long) {
        if (amount <= 0L) return
        balance = safeAdd(balance, amount)
        persist()
    }

    fun play(
        game: String,
        stake: Long,
        resolver: (Random) -> Pair<Double, String>
    ): HistoryEntry? {
        if (stake <= 0L || stake > balance) return null
        val random = roundRandom(game)
        balance -= stake
        val (multiplier, result) = resolver(random)
        val payout = (stake * multiplier.coerceAtLeast(0.0)).roundToLong()
        balance = safeAdd(balance, payout)
        val entry = HistoryEntry(game, stake, payout, result)
        history.add(0, entry)
        while (history.size > 200) history.removeLast()
        persist()
        appendHistory(entry)
        return entry
    }

    fun completeOnboarding() {
        onboardingComplete = true
        persist()
    }

    fun setDarkMode(value: Boolean) { darkMode = value; persist() }
    fun setReducedMotion(value: Boolean) { reducedMotion = value; persist() }
    fun setSecureRng(value: Boolean) { secureRng = value; persist() }
    fun setDebugSeed(value: Long) { debugSeed = value; persist() }
    fun setClientSeed(value: String) { clientSeed = value.take(80); persist() }

    fun resetLocalAccount() {
        balance = 100_000L
        darkMode = true
        reducedMotion = false
        secureRng = true
        debugSeed = 424242L
        clientSeed = "vircas-desktop"
        onboardingComplete = false
        history.clear()
        runCatching { Files.deleteIfExists(historyFile) }
        persist()
    }

    private fun load() {
        if (Files.exists(settingsFile)) {
            val p = Properties()
            Files.newInputStream(settingsFile).use(p::load)
            balance = p.getProperty("balance")?.toLongOrNull()?.coerceAtLeast(0L) ?: balance
            darkMode = p.getProperty("darkMode")?.toBooleanStrictOrNull() ?: darkMode
            reducedMotion = p.getProperty("reducedMotion")?.toBooleanStrictOrNull() ?: reducedMotion
            secureRng = p.getProperty("secureRng")?.toBooleanStrictOrNull() ?: secureRng
            debugSeed = p.getProperty("debugSeed")?.toLongOrNull() ?: debugSeed
            clientSeed = p.getProperty("clientSeed") ?: clientSeed
            onboardingComplete = p.getProperty("onboardingComplete")?.toBooleanStrictOrNull() ?: onboardingComplete
        }
        if (Files.exists(historyFile)) {
            runCatching {
                Files.readAllLines(historyFile).takeLast(200).asReversed().mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 5) null else HistoryEntry(
                        game = parts[0],
                        stake = parts[1].toLongOrNull() ?: return@mapNotNull null,
                        payout = parts[2].toLongOrNull() ?: return@mapNotNull null,
                        result = parts[3].replace("\\t", "\t"),
                        timestamp = parts[4].toLongOrNull() ?: return@mapNotNull null
                    )
                }.forEach(history::add)
            }
        }
    }

    private fun persist() {
        val p = Properties().apply {
            setProperty("balance", balance.toString())
            setProperty("darkMode", darkMode.toString())
            setProperty("reducedMotion", reducedMotion.toString())
            setProperty("secureRng", secureRng.toString())
            setProperty("debugSeed", debugSeed.toString())
            setProperty("clientSeed", clientSeed)
            setProperty("onboardingComplete", onboardingComplete.toString())
        }
        Files.newOutputStream(
            settingsFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        ).use { p.store(it, "VirCas desktop local settings") }
    }

    private fun appendHistory(entry: HistoryEntry) {
        val safeResult = entry.result.replace("\t", "\\t").replace("\n", " ")
        val row = listOf(entry.game, entry.stake, entry.payout, safeResult, entry.timestamp).joinToString("\t") + "\n"
        Files.writeString(
            historyFile,
            row,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE
        )
    }

    private fun roundRandom(game: String): Random {
        if (secureRng) return Random(secureRandom.nextLong())
        val material = "$game|$debugSeed|$clientSeed|${history.size}|${Instant.now().epochSecond / 60}"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
        val seed = digest.take(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xffL) }
        return Random(seed)
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
}
