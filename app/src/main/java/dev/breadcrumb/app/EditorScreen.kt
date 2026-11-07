package dev.breadcrumb.app

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun AddScreen(state: EditorState, actions: EditorActions) {
    Column(Modifier.fillMaxSize()) {
        val enabled = !state.saving && !state.reading
        var review by rememberSaveable { mutableStateOf(false) }
        if (review)
            PhotoReviewDialog(
                state.solutionUris,
                confirm = { photos ->
                    review = false
                    state.save(photos)
                },
                dismiss = { review = false },
            )
        Header(
            if (state.editingId == null) "New solution" else "Edit solution",
            back = { if (!state.saving) actions.back() },
            action = {
                IconButton(onClick = actions.settings, enabled = !state.saving) {
                    Icon(Icons.Outlined.Settings, "Settings")
                }
            },
        )
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScanPanel(state, actions, enabled)
            LinkPanel(state, enabled)
            PhotoPanel(state, actions, enabled)
            if (state.notebookUri == null)
                ActionButton(
                    "Choose notebook folder",
                    Icons.Outlined.FolderOpen,
                    actions.chooseFolder,
                    enabled = enabled,
                )
            ReviewButton(state, enabled) { review = true }
        }
    }
}

@Composable
private fun ScanPanel(state: EditorState, actions: EditorActions, enabled: Boolean) {
    Panel {
        Text("Find the problem", style = MaterialTheme.typography.titleMedium)
        Text(
            "Scan a screen or paste its link below.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                "Scan",
                Icons.Outlined.PhotoCamera,
                actions.scanCamera,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            ActionButton(
                "Screenshot",
                Icons.Outlined.Image,
                actions.scanImport,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
        }
        if (state.scanUri == null) {
            TextButton(onClick = actions.googleText, enabled = enabled) {
                Text("Search Google manually")
            }
        }
        state.scanUri?.let { scan -> ScanPreview(scan, state, actions, enabled) }
        if (state.status.isNotBlank())
            Text(state.status, color = Muted, style = MaterialTheme.typography.bodyMedium)
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.matches.forEach { match ->
            Surface(shape = RoundedCornerShape(16.dp), color = Sand) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(match.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        match.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { state.select(match) }) { Text("Use this problem") }
                        IconButton(onClick = { actions.openLink(match.url) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Check problem in browser")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkPanel(state: EditorState, enabled: Boolean) {
    val clipboard = LocalClipboardManager.current
    Panel {
        OutlinedTextField(
            state.url,
            state.updateUrl,
            Modifier.fillMaxWidth(),
            label = { Text("Problem link") },
            placeholder = { Text("https://leetcode.com/problems/…") },
            trailingIcon = {
                IconButton(
                    enabled = enabled,
                    onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        state.updateUrl(ProblemLinks.extractSharedLink(text) ?: text)
                    },
                ) {
                    Icon(Icons.Outlined.ContentPaste, "Paste problem link")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !state.saving,
            shape = RoundedCornerShape(16.dp),
            minLines = 1,
            maxLines = 3,
        )
        TextButton(onClick = state.cleanUrl, enabled = enabled && state.url.isNotBlank()) {
            Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clean URL")
        }
        OutlinedTextField(
            state.title,
            state.updateTitle,
            Modifier.fillMaxWidth(),
            label = { Text("Problem title") },
            enabled = !state.saving,
            shape = RoundedCornerShape(16.dp),
            maxLines = 3,
        )
        SiteDropdown(state.site, state.updateSite, enabled = !state.saving)
    }
}

@Composable
private fun PhotoPanel(state: EditorState, actions: EditorActions, enabled: Boolean) {
    Panel {
        Text(
            "Solution photos (${state.solutionUris.size})",
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.solutionUris.isNotEmpty()) {
            Text(
                "Add more pages or remove photos before saving.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            state.solutionUris.forEachIndexed { index, photo ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        photo,
                        "Solution photo ${index + 1}",
                        Modifier.weight(1f)
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Sand),
                        contentScale = ContentScale.Fit,
                    )
                    IconButton(onClick = { state.removePhoto(photo) }, enabled = enabled) {
                        Icon(Icons.Outlined.Close, "Remove photo ${index + 1}")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                "Take photo",
                Icons.Outlined.PhotoCamera,
                actions.solutionCamera,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            ActionButton(
                "Add photos",
                Icons.Outlined.Image,
                actions.solutionImport,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ReviewButton(state: EditorState, enabled: Boolean, review: () -> Unit) {
    Button(
        onClick = review,
        enabled =
            enabled &&
                !state.loading &&
                state.title.isNotBlank() &&
                state.url.isNotBlank() &&
                state.solutionUris.isNotEmpty() &&
                state.notebookUri != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        if (state.saving) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(if (state.saving) "Saving solution…" else "Review and save")
    }
}

@Composable
private fun ScanPreview(
    scan: android.net.Uri,
    state: EditorState,
    actions: EditorActions,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AsyncImage(
            scan,
            "Captured question",
            Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(14.dp)).background(Sand),
            contentScale = ContentScale.Fit,
        )
        if (state.reading) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(
            state.query,
            state.updateQuery,
            Modifier.fillMaxWidth(),
            label = { Text("Words from your scan") },
            minLines = 2,
            maxLines = 4,
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
        )
        ActionButton(
            "Search Google with text",
            Icons.Outlined.Search,
            actions.googleText,
            enabled = enabled,
        )
        ActionButton(
            "Search with this photo",
            Icons.Outlined.ImageSearch,
            actions.googleImage,
            enabled = enabled,
        )
        if (state.hasSearchKey)
            TextButton(
                onClick = state.findMatches,
                enabled = enabled && !state.searching && state.query.isNotBlank(),
            ) {
                Text(if (state.searching) "Finding matches…" else "Find matching problem")
            }
    }
}
