package com.vircas.mobile.game

import com.vircas.mobile.core.game.RoundMemory
import com.vircas.mobile.core.game.WagerRecord
import com.vircas.mobile.core.game.WagerRules
import org.junit.Assert.*
import org.junit.Test

class WagerRulesTest {
    private fun reserved() = WagerRecord("w1", "Blackjack", 1_000L, 1L, "seed", "client")

    @Test fun repeatedSettlementReturnsOriginalReceiptWithoutAnotherCredit() {
        val original = reserved()
        val first = WagerRules.settle(original, original.active(), 99_000L, 2.0, "WIN", "", 2L)
        val retry = WagerRules.settle(first.record, original.active(), first.balance, 100.0, "different", "", 3L)
        assertEquals(101_000L, first.balance)
        assertEquals(first.balance, retry.balance)
        assertEquals(first.record.receipt(), retry.record.receipt())
        assertFalse(retry.changed)
    }

    @Test fun cancelAfterSettlementDoesNotRefundTheStake() {
        val r = reserved()
        val settled = WagerRules.settle(r, r.active(), 99_000L, 0.0, "LOSS", "", 2L)
        val cancelled = WagerRules.cancel(settled.record, r.active(), settled.balance, 3L)
        assertEquals(99_000L, cancelled.balance)
        assertFalse(cancelled.changed)
    }

    @Test fun unusedReservationIsRefundedOnlyOnce() {
        val r = reserved()
        val first = WagerRules.cancel(r, r.active(), 99_000L, 2L)
        val retry = WagerRules.cancel(first.record, r.active(), first.balance, 3L)
        assertEquals(100_000L, first.balance)
        assertEquals(first.balance, retry.balance)
        assertEquals(WagerRecord.CANCELLED, retry.record.status)
        assertFalse(retry.changed)
    }

    @Test(expected = IllegalStateException::class)
    fun settlementAfterCancellationIsRejected() {
        val r = reserved()
        val cancelled = WagerRules.cancel(r, r.active(), 99_000L, 2L)
        WagerRules.settle(cancelled.record, r.active(), cancelled.balance, 2.0, "WIN", "", 3L)
    }

    @Test fun doubleDownCannotChargeTheSameRevisionTwice() {
        val r = reserved()
        val increased = requireNotNull(WagerRules.increase(r, r.active(), 1_000L, 99_000L))
        assertEquals(2_000L, increased.record.stake)
        assertEquals(98_000L, increased.balance)
        assertNull(WagerRules.increase(increased.record, r.active(), 1_000L, increased.balance))
    }

    @Test fun aLockedLossCannotBecomeAnExitRefundOrAnOldCashout() {
        val r = reserved()
        val loss = WagerRules.checkpoint(r, r.active(), 0.0, "Wrong guess", "next card", true)
        val cancelled = WagerRules.cancel(loss, r.active(), 99_000L, 2L)
        assertEquals(99_000L, cancelled.balance)
        assertEquals(WagerRecord.SETTLED, cancelled.record.status)
        assertEquals("Wrong guess", cancelled.record.result)
        val oldCashout = WagerRules.settle(loss, r.active(), 99_000L, 3.0, "Old cashout", "", 2L)
        assertEquals(0.0, oldCashout.record.multiplier, 0.0)
    }

    @Test fun interruptedMultiStepGameUsesTheLatestSavedBank() {
        val r = reserved()
        val safe = WagerRules.checkpoint(r, r.active(), 1.5, "Safe", "level 1", false)
        val next = WagerRules.checkpoint(safe, r.active(), 2.0, "Safe", "level 2", false)
        val recovered = WagerRules.cancel(next, r.active(), 99_000L, 3L)
        assertEquals(101_000L, recovered.balance)
        assertEquals(2.0, recovered.record.multiplier, 0.0)
    }

    @Test fun terminalCheckpointCannotBeReplaced() {
        val r = reserved()
        val locked = WagerRules.checkpoint(r, r.active(), 0.0, "LOSS", "", true)
        assertEquals(locked, WagerRules.checkpoint(locked, r.active(), 25.0, "WIN", "", true))
    }

    @Test fun payoutAndCreditSaturateWithoutOverflow() {
        assertEquals(Long.MAX_VALUE, WagerRules.payout(Long.MAX_VALUE, 25.0))
        assertEquals(Long.MAX_VALUE, WagerRules.credit(Long.MAX_VALUE - 5L, 100L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFinitePayoutIsRejected() { WagerRules.payout(1_000L, Double.NaN) }

    @Test fun newCompositionGetsTheSameLogicalStateUntilAccountReset() {
        val memory = RoundMemory()
        val original = mutableListOf("dealt", "hit")
        assertSame(original, memory.remember("blackjack") { original })
        assertSame(original, memory.remember("blackjack") { mutableListOf("new game") })
        memory.clear()
        assertNotSame(original, memory.remember("blackjack") { mutableListOf("new game") })
    }
}
