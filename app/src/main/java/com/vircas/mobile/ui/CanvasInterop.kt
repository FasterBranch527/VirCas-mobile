package com.vircas.mobile.ui

import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas as frameworkNativeCanvas

internal val Canvas.nativeCanvas: AndroidCanvas
    get() = this.frameworkNativeCanvas
