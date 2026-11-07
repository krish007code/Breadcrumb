package dev.breadcrumb.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun NotebookScreen(
    state: NotebookState,
    chooseFolder: () -> Unit,
    settings: () -> Unit,
    edit: (NotebookEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        var filter by rememberSaveable { mutableStateOf("") }
        var siteFilter by rememberSaveable { mutableStateOf<String?>(null) }
        var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
        val pending = state.entries.firstOrNull { it.id == deleteId }
        if (pending != null)
            DeleteSolutionDialog(
                pending,
                {
                    deleteId = null
                    state.deleteEntry(pending)
                },
                { deleteId = null },
            )
        Header(
            "Breadcrumb",
            action = {
                IconButton(onClick = settings) { Icon(Icons.Outlined.Settings, "Settings") }
            },
        )
        TitleFilter(filter) { filter = it }
        SiteFilter(siteFilter) { siteFilter = it }
        when {
            state.notebookUri == null ->
                ChooseNotebookPanel(chooseFolder)
            state.loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink)
                }
            state.folderError != null ->
                EmptyPanel(
                    Icons.Outlined.FolderOpen,
                    "Open your notebook again",
                    state.folderError.orEmpty(),
                    "Choose folder",
                    chooseFolder,
                )
            state.entries.isEmpty() ->
                EmptyPanel(
                    Icons.Outlined.EditNote,
                    "Your first breadcrumb",
                    "Add a problem link and a photo of your solution. Come back whenever you want to revise it.",
                    "Add a solution",
                    { state.navigate("add") },
                )
            else -> {
                val visible =
                    state.entries.filter {
                        ProblemLinks.matches(it.title, filter) &&
                            (siteFilter == null || it.site.name == siteFilter)
                    }
                FilteredEntries(visible, state, edit) { deleteId = it.id }
            }
        }
    }
}

@Composable
private fun SiteFilter(siteFilter: String?, select: (String?) -> Unit) {
    var siteMenu by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 24.dp)) {
        TextButton(onClick = { siteMenu = true }) {
            Text(ProblemSite.entries.firstOrNull { it.name == siteFilter }?.label ?: "All sites")
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded = siteMenu, onDismissRequest = { siteMenu = false }) {
            DropdownMenuItem(
                text = { Text("All sites") },
                onClick = {
                    select(null)
                    siteMenu = false
                },
            )
            ProblemSite.entries.forEach { site ->
                DropdownMenuItem(
                    text = { Text(site.label) },
                    onClick = {
                        select(site.name)
                        siteMenu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FilteredEntries(
    visible: List<NotebookEntry>,
    state: NotebookState,
    edit: (NotebookEntry) -> Unit,
    remove: (NotebookEntry) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 26.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${visible.size} ${if (visible.size == 1) "solution" else "solutions"}",
                Modifier.weight(1f),
                color = Muted,
            )
            IconButton(onClick = state.refresh) { Icon(Icons.Outlined.Refresh, "Refresh notebook") }
        }
        if (visible.isEmpty()) {
            EmptyPanel(
                Icons.Outlined.Search,
                "No matching titles",
                "Try a different word from the problem title.",
            )
        } else
            LazyColumn(
                contentPadding =
                    PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = NotebookEntry::id) { entry ->
                    SolutionRow(entry, onEdit = { edit(entry) }, onDelete = { remove(entry) }) {
                        state.open(entry)
                    }
                }
            }
    }
}

@Composable
private fun TitleFilter(filter: String, update: (String) -> Unit) {
    OutlinedTextField(
        value = filter,
        onValueChange = update,
        placeholder = { Text("Search problem titles") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (filter.isNotEmpty())
                IconButton(onClick = { update("") }) { Icon(Icons.Outlined.Close, "Clear search") }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        singleLine = true,
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                unfocusedBorderColor = Border,
            ),
    )
}

@Composable
private fun ChooseNotebookPanel(chooseFolder: () -> Unit) {
                EmptyPanel(
                    Icons.Outlined.FolderOpen,
                    "A place for your solutions",
                    "Choose a folder on your phone. Breadcrumb will keep each problem " +
                        "link and your handwritten solution together there.",
                    "Choose notebook folder",
                    chooseFolder,
                )
}
