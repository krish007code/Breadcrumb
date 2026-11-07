package dev.breadcrumb.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
internal fun DeleteSolutionDialog(entry: NotebookEntry, confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Delete this solution?") },
        text = {
            Text(
                "${entry.title} and all ${entry.photoUris.size} solution photos will " +
                    "be removed from your notebook folder. This cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = confirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Keep solution") } },
    )
}

@Composable
internal fun PhotoViewer(photos: List<Uri>, initialIndex: Int, close: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(initialIndex) }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        key(photos[index]) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, MaxZoom)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
            ) {
                AsyncImage(
                    photos[index],
                    "Your solution. Pinch to zoom and drag to pan.",
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                    contentScale = ContentScale.Fit,
                )
                IconButton(
                    onClick = close,
                    Modifier.align(Alignment.TopEnd).padding(16.dp).background(Ink, CircleShape),
                ) {
                    Icon(Icons.Outlined.Close, "Close enlarged photo", tint = Color.White)
                }
                Row(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Ink, RoundedCornerShape(24.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { index-- }, enabled = index > 0) {
                        Icon(Icons.Outlined.ChevronLeft, "Previous photo", tint = Color.White)
                    }
                    Text("${index + 1} / ${photos.size}", color = Color.White)
                    IconButton(onClick = { index++ }, enabled = index < photos.lastIndex) {
                        Icon(Icons.Outlined.ChevronRight, "Next photo", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhotoReviewDialog(
    photos: List<Uri>,
    confirm: (List<Uri>) -> Unit,
    dismiss: () -> Unit,
) {
    var kept by rememberSaveable(photos) { mutableStateOf(ArrayList(photos.map(Uri::toString))) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Choose photos to keep") },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(photos, key = Uri::toString) { photo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            modifier =
                                Modifier.semantics {
                                    contentDescription =
                                        "Keep solution photo ${photos.indexOf(photo) + 1}"
                                },
                            checked = photo.toString() in kept,
                            onCheckedChange = { selected ->
                                kept =
                                    ArrayList(
                                        if (selected) kept + photo.toString()
                                        else kept - photo.toString()
                                    )
                            },
                        )
                        AsyncImage(
                            photo,
                            "Solution photo ${photos.indexOf(photo) + 1}",
                            Modifier.weight(1f)
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Sand),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirm(photos.filter { it.toString() in kept }) },
                enabled = kept.isNotEmpty(),
            ) {
                Text("Save ${kept.size} ${if (kept.size == 1) "photo" else "photos"}")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Back") } },
    )
}

private const val MaxZoom = 6f
