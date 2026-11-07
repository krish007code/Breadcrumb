package dev.breadcrumb.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun NotebookFrame(
    frame: FrameState,
    snackbar: SnackbarHostState,
    navigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val screenState = rememberSaveableStateHolder()
    Scaffold(
        modifier = modifier,
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!frame.welcome && showsNavigation(frame.page, frame.origin)) {
                NotebookNavigation(frame.page, navigate)
            }
        },
        floatingActionButton = {
            if (!frame.welcome && frame.page == "notebook") {
                ExtendedFloatingActionButton(
                    onClick = { navigate("add") },
                    containerColor = Ink,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(28.dp),
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(if (frame.draftStarted) "Continue draft" else "New solution") },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                screenState.SaveableStateProvider(if (frame.welcome) "welcome" else frame.page) { content() }
            }
        }
    }
}

private fun showsNavigation(page: String, settingsReturn: String) =
    when (page) {
        "notebook",
        "progress" -> true
        "settings" -> settingsReturn != "add"
        else -> false
    }

internal data class FrameState(
    val page: String,
    val welcome: Boolean,
    val origin: String,
    val draftStarted: Boolean,
)
