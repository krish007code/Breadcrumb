package dev.breadcrumb.app

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun Header(
    title: String,
    back: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null)
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        else {
            Image(
                painterResource(R.drawable.ic_breadcrumb),
                null,
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        action?.invoke()
    }
}

@Composable
internal fun EmptyPanel(
    icon: ImageVector,
    title: String,
    text: String,
    button: String? = null,
    action: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.size(68.dp).background(Amber, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(text, color = Muted)
        if (button != null)
            Button(
                onClick = action,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(button)
            }
    }
}

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SolutionRow(
    entry: NotebookEntry,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    action: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier =
                Modifier.clip(RoundedCornerShape(22.dp))
                    .combinedClickable(
                        onClick = action,
                        onLongClickLabel = "Edit or delete solution",
                        onLongClick =
                            if (onEdit != null || onDelete != null) ({ menu = true }) else null,
                    ),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
        ) {
            SolutionRowContent(entry)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onEdit != null)
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = {
                        menu = false
                        onEdit()
                    },
                )
            if (onDelete != null)
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
        }
    }
}

@Composable
internal fun ActionButton(
    text: String,
    icon: ImageVector,
    action: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
internal fun SiteDropdown(
    site: ProblemSite,
    select: (ProblemSite) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Site", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(site.label, Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, "Choose site")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ProblemSite.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            select(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SolutionRowContent(entry: NotebookEntry) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            entry.photoUri,
            "Solution thumbnail",
            Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Sand),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.site.let {
                    if (it == ProblemSite.OTHER) Uri.parse(entry.url).host.orEmpty() else it.label
                },
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (entry.photoUris.size > 1)
                Text(
                    "${entry.photoUris.size} photos",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
    }
}
