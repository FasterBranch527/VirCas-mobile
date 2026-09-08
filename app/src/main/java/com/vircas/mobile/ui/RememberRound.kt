package com.vircas.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Keep logical game state through Activity recreation; animations may restart independently. */
@Composable
internal fun <T> rememberRound(viewModel: AppViewModel, key: String, initializer: () -> T): T =
    remember(viewModel, viewModel.roundGeneration, key) { viewModel.retainRound(key, initializer) }
