package com.vircas.mobile.core.sound

import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager {
    enum class Cue { TAP, WIN, LOSS, REVEAL, LEVEL_UP }

    private var tone: ToneGenerator? = ToneGenerator(AudioManager.STREAM_MUSIC, 55)

    fun play(cue: Cue, enabled: Boolean) {
        if (!enabled) return
        val toneType = when (cue) {
            Cue.TAP -> ToneGenerator.TONE_PROP_BEEP
            Cue.WIN -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            Cue.LOSS -> ToneGenerator.TONE_PROP_NACK
            Cue.REVEAL -> ToneGenerator.TONE_PROP_ACK
            Cue.LEVEL_UP -> ToneGenerator.TONE_CDMA_CONFIRM
        }
        tone?.startTone(toneType, when (cue) {
            Cue.TAP -> 45
            Cue.REVEAL -> 70
            Cue.WIN, Cue.LEVEL_UP -> 160
            Cue.LOSS -> 110
        })
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
