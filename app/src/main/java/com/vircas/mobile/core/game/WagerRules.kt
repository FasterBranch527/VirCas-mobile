package com.vircas.mobile.core.game

data class ActiveWager(val id: String, val game: String, val stake: Long, val startedAt: Long, val revision: Long = 0L)

data class RoundReceipt(val wagerId: String, val roundId: String, val game: String, val stake: Long, val payout: Long, val multiplier: Double, val result: String) {
    val profitLoss: Long get() = payout - stake
}

/** Durable state; probabilities and payout tables stay in the existing engines. */
data class WagerRecord(
    val id: String,
    val game: String,
    val stake: Long,
    val startedAt: Long,
    val generatedSeed: String,
    val clientSeed: String,
    val revision: Long = 0L,
    val status: String = ACTIVE,
    val checkpointMultiplier: Double? = null,
    val checkpointResult: String? = null,
    val checkpointDetails: String = "",
    val checkpointTerminal: Boolean = false,
    val payout: Long = 0L,
    val multiplier: Double = 0.0,
    val result: String = "",
    val details: String = "",
    val completedAt: Long = 0L,
    val progressSynced: Boolean = false
) {
    fun active() = ActiveWager(id, game, stake, startedAt, revision)
    fun receipt() = RoundReceipt(id, "round-$id", game, stake, payout, multiplier, result)
    companion object {
        const val ACTIVE = "ACTIVE"
        const val SETTLED = "SETTLED"
        const val CANCELLED = "CANCELLED"
    }
}

data class WagerChange(val record: WagerRecord, val balance: Long, val changed: Boolean = true)

/** Pure transitions used by Room transactions and the JVM regression tests. */
object WagerRules {
    fun credit(balance: Long, amount: Long): Long {
        require(balance >= 0L && amount >= 0L)
        return balance + amount.coerceAtMost(Long.MAX_VALUE - balance)
    }
    fun payout(stake: Long, multiplier: Double): Long {
        require(stake > 0L && multiplier.isFinite() && multiplier >= 0.0)
        val value = stake.toDouble() * multiplier
        return if (!value.isFinite() || value >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else value.toLong().coerceAtLeast(0L)
    }
    fun matches(record: WagerRecord, request: ActiveWager): Boolean =
        record.id == request.id && record.game == request.game && record.stake == request.stake && record.revision == request.revision

    fun increase(record: WagerRecord, request: ActiveWager, extra: Long, balance: Long): WagerChange? {
        if (record.status != WagerRecord.ACTIVE || record.checkpointTerminal || !matches(record, request)) return null
        if (extra <= 0L || extra > balance || record.stake > Long.MAX_VALUE - extra) return null
        return WagerChange(record.copy(stake = record.stake + extra, revision = record.revision + 1L), balance - extra)
    }
    fun checkpoint(record: WagerRecord, request: ActiveWager, multiplier: Double, result: String, details: String, terminal: Boolean): WagerRecord {
        require(multiplier.isFinite() && multiplier >= 0.0)
        if (record.status != WagerRecord.ACTIVE || record.checkpointTerminal) return record
        check(matches(record, request)) { "Stale wager checkpoint" }
        return record.copy(checkpointMultiplier = multiplier, checkpointResult = result, checkpointDetails = details, checkpointTerminal = terminal)
    }
    fun settle(record: WagerRecord, request: ActiveWager, balance: Long, multiplier: Double, result: String, details: String, now: Long): WagerChange {
        if (record.status == WagerRecord.SETTLED) return WagerChange(record, balance, false)
        check(record.status == WagerRecord.ACTIVE) { "Wager is already cancelled" }
        check(matches(record, request)) { "Stale wager settlement" }
        val locked = record.checkpointTerminal
        val finalMultiplier = if (locked) requireNotNull(record.checkpointMultiplier) else multiplier
        val value = payout(record.stake, finalMultiplier)
        return WagerChange(record.copy(
            status = WagerRecord.SETTLED, payout = value, multiplier = finalMultiplier,
            result = if (locked) requireNotNull(record.checkpointResult) else result,
            details = if (locked) record.checkpointDetails else details,
            completedAt = now
        ), credit(balance, value))
    }
    fun cancel(record: WagerRecord, request: ActiveWager, balance: Long, now: Long): WagerChange {
        if (record.status != WagerRecord.ACTIVE) return WagerChange(record, balance, false)
        check(matches(record, request)) { "Stale wager cancellation" }
        // A saved outcome is settled, never erased by a navigation refund.
        if (record.checkpointMultiplier != null) return settle(record, request, balance, record.checkpointMultiplier, record.checkpointResult.orEmpty(), record.checkpointDetails, now)
        return WagerChange(record.copy(status = WagerRecord.CANCELLED, completedAt = now, progressSynced = true), credit(balance, record.stake))
    }
}
