package dev.breadcrumb.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun SolutionScreen(
    state: DetailState,
    back: () -> Unit,
    openLink: (String) -> Unit,
    edit: (NotebookEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        var fullscreen by rememberSaveable { mutableIntStateOf(-1) }
        var confirmDelete by rememberSaveable { mutableStateOf(false) }
        Header(
            "Your solution",
            back = { if (!state.saving) back() },
            action = {
                IconButton(
                    onClick = { state.selectedEntry?.let(edit) },
                    enabled = !state.saving && state.selectedEntry != null,
                ) {
                    Icon(Icons.Outlined.Edit, "Edit solution")
                }
                IconButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.saving && state.selectedEntry != null,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete solution")
                }
            },
        )
        val entry = state.selectedEntry
        if (entry == null) {
            if (state.loading)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            else
                EmptyPanel(
                    Icons.Outlined.FolderOpen,
                    "Solution unavailable",
                    "Refresh the notebook or choose its folder again.",
                    "Back to notebook",
                    back,
                )
            return@Column
        }
        SolutionPages(entry, state, openLink) { fullscreen = it }
        if (confirmDelete)
            DeleteSolutionDialog(
                entry,
                {
                    confirmDelete = false
                    state.deleteEntry(entry)
                },
                { confirmDelete = false },
            )
        if (fullscreen >= 0)
            PhotoViewer(entry.photoUris, fullscreen.coerceAtMost(entry.photoUris.lastIndex)) {
                fullscreen = -1
            }
    }
}

@Composable
private fun SolutionPages(
    entry: NotebookEntry,
    state: DetailState,
    openLink: (String) -> Unit,
    enlarge: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(entry.title, style = MaterialTheme.typography.headlineMedium)
        SiteDropdown(entry.site, { state.changeEntrySite(entry, it) }, enabled = !state.saving)
        ActionButton(
            "Open question",
            Icons.AutoMirrored.Outlined.OpenInNew,
            { openLink(entry.url) },
        )
        val pager = rememberPagerState(pageCount = { entry.photoUris.size })
        Text(
            "Photo ${pager.currentPage + 1} of ${entry.photoUris.size}",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            Modifier.weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                AsyncImage(
                    entry.photoUris[index],
                    "Solution photo ${index + 1}. Tap to enlarge.",
                    Modifier.fillMaxSize().clickable { enlarge(index) },
                    contentScale = ContentScale.Fit,
                )
            }
            Icon(
                Icons.Outlined.ZoomIn,
                "Enlarge solution",
                Modifier.align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(Paper, CircleShape)
                    .padding(10.dp),
            )
        }
    }
}
