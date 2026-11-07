package dev.breadcrumb.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(state: SettingsState, back: () -> Unit, chooseFolder: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("Settings", back)
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Panel {
                Text("Notebook folder", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.notebookUri == null) "Choose where to keep your solutions."
                    else state.notebookName,
                    color = Muted,
                )
                Text(
                    "Copy this entire folder to your hard disk whenever you want a " +
                        "backup. Choose the same folder to reopen your notebook after " +
                        "reinstalling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                ActionButton(
                    if (state.notebookUri == null) "Choose folder" else "Change or reopen folder",
                    Icons.Outlined.FolderOpen,
                    chooseFolder,
                    enabled = !state.saving && !state.loading,
                )
            }
            SearchSettings(state)
            TextButton(onClick = state.showTutorial) { Text("View welcome tutorial") }
        }
    }
}

@Composable
private fun SearchSettings(state: SettingsState) {
    var key by remember { mutableStateOf("") }
    Panel {
        Text("Automatic scan lookup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add your TinyFish Search API key to get problem-link suggestions " +
                "after scanning. Recognized words are sent to TinyFish. Google search " +
                "and pasting links work without a key.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        if (state.hasSearchKey)
            Text(
                "A search key is saved on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        OutlinedTextField(
            key,
            { key = it },
            Modifier.fillMaxWidth(),
            label = { Text(if (state.hasSearchKey) "Replace API key" else "TinyFish API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(
            onClick = {
                state.saveSearchKey(key)
                key = ""
            },
            enabled = key.isNotBlank(),
        ) {
            Text("Save key")
        }
        if (state.hasSearchKey)
            TextButton(onClick = { state.saveSearchKey("") }) { Text("Remove key") }
    }
}
