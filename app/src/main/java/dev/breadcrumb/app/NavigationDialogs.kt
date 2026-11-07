package dev.breadcrumb.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun NotebookNavigation(page: String, navigate: (String) -> Unit) {
    NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = page == "notebook",
            onClick = { navigate("notebook") },
            icon = { Icon(Icons.Outlined.Home, null) },
            label = { Text("Notebook") },
        )
        NavigationBarItem(
            selected = page == "progress",
            onClick = { navigate("progress") },
            icon = { Icon(Icons.Outlined.BarChart, null) },
            label = { Text("Progress") },
        )
        NavigationBarItem(
            selected = page == "settings",
            onClick = { if (page != "settings") navigate("settings") },
            icon = { Icon(Icons.Outlined.Settings, null) },
            label = { Text("Settings") },
        )
    }
}

@Composable
internal fun LensDialog(
    scan: android.net.Uri?,
    report: (String) -> Unit,
    exportScan: (String) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text("Search this photo") },
        text = {
            Text(
                "The photo is sent with ‘Give me the original link for this " +
                    "question.’ The request is also copied so you can paste it into Ask " +
                    "about this image if Google leaves the text out. Share or paste the " +
                    "question link back here."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dismiss()
                    scan?.let { photo ->
                        runCatching { BrowserActions.searchImage(context, photo) }
                            .onFailure {
                                report(
                                    "No image app accepted the scan. Use Search with this photo again and " +
                                        "choose Save and open Lens."
                                )
                            }
                    }
                }
            ) {
                Text("Open with Google")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dismiss()
                    val scan = scan ?: return@TextButton
                    val extension =
                        when (context.contentResolver.getType(scan)) {
                            "image/png" -> "png"
                            "image/webp" -> "webp"
                            "image/heic",
                            "image/heif" -> "heic"
                            else -> "jpg"
                        }
                    exportScan("Breadcrumb-scan.$extension")
                }
            ) {
                Text("Save and open Lens")
            }
        },
    )
}

@Composable
internal fun ReplaceDraftDialog(entry: NotebookEntry, edit: () -> Unit, dismiss: () -> Unit) {

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text("Replace your current draft?") },
        text = { Text("Open ${entry.title} for editing and discard the unfinished draft?") },
        confirmButton = { TextButton(onClick = { edit() }) { Text("Discard draft and edit") } },
        dismissButton = { TextButton(onClick = { dismiss() }) { Text("Keep draft") } },
    )
}
