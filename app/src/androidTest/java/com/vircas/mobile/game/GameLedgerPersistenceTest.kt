package com.vircas.mobile.game

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vircas.mobile.core.data.AppDatabase
import com.vircas.mobile.core.data.FairnessRepository
import com.vircas.mobile.core.data.GameHistoryRepository
import com.vircas.mobile.core.data.WalletRow
import com.vircas.mobile.core.game.GameLedger
import com.vircas.mobile.core.game.RoundProgressSink
import com.vircas.mobile.core.game.WagerRecord
import com.vircas.mobile.core.wallet.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameLedgerPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var wallet: WalletRepository
    private lateinit var ledger: GameLedger
    private val projected = linkedSetOf<String>()
    private var failProjection = false
    private val sink = object : RoundProgressSink {
        override suspend fun recordSettledRound(record: WagerRecord) {
            if (failProjection) error("Injected projection failure")
            projected += record.id
        }
        override suspend fun resetRoundProgress() { projected.clear() }
    }

    @Before fun prepare() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        // Never read or mutate the installed application's real wallet/progression DataStores.
        db.ledgerDao().initializeWallet(WalletRow(balance = 100_000L))
        wallet = WalletRepository(context, db)
        ledger = newLedger()
    }

    private fun newLedger() = GameLedger(wallet, GameHistoryRepository(db.gameHistoryDao()), sink, FairnessRepository(db.fairnessRoundDao()))
    @After fun close() { db.close() }

    @Test fun concurrentSettlementsPayAndRecordExactlyOnce() = runBlocking {
        val wager = requireNotNull(ledger.begin("Dice", 1_000L, "fixed", "client"))
        val receipts = coroutineScope {
            List(8) { async(Dispatchers.Default) { ledger.settle(wager, 2.0, "WIN") } }.awaitAll()
        }
        assertTrue(receipts.all { it == receipts.first() })
        assertEquals(101_000L, wallet.balance.first())
        assertEquals(1, db.gameHistoryDao().recent().first().size)
        assertEquals(1, db.fairnessRoundDao().recent().first().size)
        assertEquals(setOf(wager.id), projected)
    }

    @Test fun duplicateStartDoesNotReserveAnotherStake() = runBlocking {
        assertNotNull(ledger.begin("Mines", 1_000L))
        assertNull(ledger.begin("Mines", 1_000L))
        assertEquals(99_000L, wallet.balance.first())
    }

    @Test fun historyWriteFailureRollsBackTheWalletAndWagerTogether() = runBlocking {
        val wager = requireNotNull(ledger.begin("Dice", 1_000L))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_history BEFORE INSERT ON game_history BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try {
            ledger.settle(wager, 2.0, "WIN")
            fail("Expected injected write failure")
        } catch (_: android.database.SQLException) { }
        assertEquals(99_000L, wallet.balance.first())
        assertEquals(WagerRecord.ACTIVE, db.ledgerDao().wager(wager.id)?.record?.status)
        assertTrue(db.gameHistoryDao().recent().first().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_history")
        ledger.settle(wager, 2.0, "WIN")
        assertEquals(101_000L, wallet.balance.first())
        assertEquals(1, db.gameHistoryDao().recent().first().size)
    }

    @Test fun recoverySettlesLockedOutcomeWithoutAnotherDebit() = runBlocking {
        val wager = requireNotNull(ledger.begin("Coinflip", 1_000L, "original-seed", "original-client"))
        ledger.checkpoint(wager, 1.98, "HEADS")
        newLedger().recoverInterruptedRounds()
        newLedger().recoverInterruptedRounds()
        assertEquals(100_980L, wallet.balance.first())
        val fairness = db.fairnessRoundDao().recent().first().single()
        assertEquals("original-seed", fairness.generatedSeed)
        assertEquals("original-client", fairness.clientSeed)
    }

    @Test fun recoveryRefundsAnUnusedReservationOnce() = runBlocking {
        ledger.begin("Blackjack", 1_000L)
        newLedger().recoverInterruptedRounds()
        newLedger().recoverInterruptedRounds()
        assertEquals(100_000L, wallet.balance.first())
        assertTrue(db.gameHistoryDao().recent().first().isEmpty())
    }

    @Test fun failedProgressProjectionCannotConvertAWinIntoARefund() = runBlocking {
        val wager = requireNotNull(ledger.begin("Dice", 1_000L))
        failProjection = true
        ledger.settle(wager, 2.0, "WIN")
        ledger.cancel(wager)
        assertEquals(101_000L, wallet.balance.first())
        assertEquals(1, db.ledgerDao().pendingProgress().size)
        failProjection = false
        ledger.flushProgress()
        ledger.flushProgress()
        assertEquals(setOf(wager.id), projected)
        assertTrue(db.ledgerDao().pendingProgress().isEmpty())
    }

    @Test fun resetInvalidatesCallbacksFromAnOldRound() = runBlocking {
        val wager = requireNotNull(ledger.begin("Dice", 1_000L))
        ledger.resetLocalAccount()
        try { ledger.settle(wager, 25.0, "late callback"); fail("Old wager must not survive reset") }
        catch (_: IllegalArgumentException) { }
        ledger.cancel(wager)
        assertEquals(100_000L, wallet.balance.first())
    }
}
