package com.vircas.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Don't permit new input until startup recovery and pending local writes are resolved. */
@Composable
internal fun RoundPersistenceGate(viewModel: AppViewModel, content: @Composable () -> Unit) {
    val ready by viewModel.ready.collectAsState()
    val error by viewModel.saveError.collectAsState()
    if (ready) content()
    else Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator()
        Text("Restoring local rounds…")
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Local save needs attention") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.retrySaving() }) { Text("Retry") } }
        )
    }
}
