package ru.kino.dev.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.orbitmvi.orbit.compose.collectAsState
import ru.kino.dev.R

@Composable
fun HomeScreen(processor: HomeProcessor = hiltViewModel()) {
    val state by processor.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.home_greeting))
            Text(text = stringResource(R.string.home_clicks, state.clicksCount))
            Button(onClick = { processor.onClick() }) {
                Text(text = stringResource(R.string.home_click_me))
            }

            // Step 22: the native bridge, checked from the app itself
            Text(
                text = state.nativeBuildInfo
                    ?.let { stringResource(R.string.home_native_build_info, it) }
                    ?: stringResource(R.string.home_native_loading),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            state.generatedPassword?.let { password ->
                Text(text = stringResource(R.string.home_generated_password, password))
            }

            state.nativeError?.let { error ->
                Text(
                    text = stringResource(R.string.home_native_error, error),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = { processor.onGeneratePassword() },
                enabled = !state.isNativeBusy,
            ) {
                Text(
                    text = stringResource(
                        if (state.isNativeBusy) R.string.home_native_busy else R.string.home_generate_password,
                    ),
                )
            }

            Button(
                onClick = { processor.onProvokeCoreError() },
                enabled = !state.isNativeBusy,
            ) {
                Text(text = stringResource(R.string.home_provoke_error))
            }

            Button(
                onClick = { processor.onProvokeBridgeError() },
                enabled = !state.isNativeBusy,
            ) {
                Text(text = stringResource(R.string.home_provoke_unknown_command))
            }
        }
    }
}
