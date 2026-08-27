package com.vircas.mobile.ui

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Keeps low-level native text drawing local to the custom roulette renderer. */
internal inline fun DrawScope.drawIntoCanvas(block: (Canvas) -> Unit) {
    block(drawContext.canvas)
}
