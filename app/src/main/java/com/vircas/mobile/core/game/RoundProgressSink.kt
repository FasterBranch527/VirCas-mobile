package com.vircas.mobile.core.game

/** Idempotent projection of a committed round; a projection failure must not undo wallet writes. */
interface RoundProgressSink {
    suspend fun recordSettledRound(record: WagerRecord)
    suspend fun resetRoundProgress()
}
