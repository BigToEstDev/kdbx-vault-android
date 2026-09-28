package ru.kino.dev.databasecheck

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.orbitmvi.orbit.compose.collectAsState
import ru.kino.dev.R

/**
 * The screen that proves the bridge on a real file.
 *
 * Both pickers are the system ones: creating a document and opening one. Nothing here knows about uris
 * beyond handing them to the processor - permissions, reading and the truncating write live behind the
 * repository.
 */
@Composable
fun DatabaseCheckScreen(processor: DatabaseCheckProcessor = hiltViewModel()) {
    val state by processor.collectAsState()

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(KDBX_MIME_TYPE),
    ) { uri ->
        uri?.let { processor.onFileCreated(it.toString()) }
    }

    // Any mime type: providers disagree about what a .kdbx is, and a filter that looks right on one
    // device hides the file on another
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { processor.onFilePicked(it.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.database_check_title),
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = processor::onPasswordChange,
            label = { Text(text = stringResource(R.string.database_check_password)) },
            singleLine = true,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { createFile.launch(DEFAULT_FILE_NAME) },
                enabled = !state.isBusy,
            ) {
                Text(text = stringResource(R.string.database_check_create))
            }

            Button(
                onClick = { openFile.launch(arrayOf(ANY_MIME_TYPE)) },
                enabled = !state.isBusy,
            ) {
                Text(text = stringResource(R.string.database_check_open))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = processor::onSave,
                enabled = !state.isBusy && state.isOpen,
            ) {
                Text(text = stringResource(R.string.database_check_save))
            }

            Button(
                onClick = processor::onClose,
                enabled = !state.isBusy && state.isOpen,
            ) {
                Text(text = stringResource(R.string.database_check_close))
            }
        }

        if (state.isBusy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text(text = stringResource(R.string.database_check_busy))
            }
        }

        state.databaseName?.let { name ->
            Text(text = stringResource(R.string.database_check_database, name))
        }

        state.fileName?.let { name ->
            Text(text = stringResource(R.string.database_check_file, name))
        }

        state.error?.let { error ->
            Text(
                text = stringResource(R.string.database_check_error, error),
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.recent.isNotEmpty()) {
            Text(
                text = stringResource(R.string.database_check_recent),
                style = MaterialTheme.typography.labelLarge,
            )
            state.recent.forEach { remembered ->
                // Opening from here goes through the stored uri and no picker, which is what proves the
                // access survived the process
                TextButton(
                    onClick = { processor.onFilePicked(remembered.uri) },
                    enabled = !state.isBusy,
                ) {
                    Text(
                        text = remembered.fileName
                            ?: remembered.databaseName
                            ?: remembered.uri,
                    )
                }
            }
        }

        if (state.log.isNotEmpty()) {
            Text(
                text = stringResource(R.string.database_check_log),
                style = MaterialTheme.typography.labelLarge,
            )
            state.log.asReversed().forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// The mime type a new document is created with. There is no registered type for kdbx, and octet-stream is
// what every provider accepts
private const val KDBX_MIME_TYPE = "application/octet-stream"
private const val ANY_MIME_TYPE = "*/*"
private const val DEFAULT_FILE_NAME = "check.kdbx"
