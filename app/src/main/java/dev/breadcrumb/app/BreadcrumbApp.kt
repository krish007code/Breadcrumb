package dev.breadcrumb.app

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun BreadcrumbApp(vm: NotebookViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    var settingsReturn by rememberSaveable { mutableStateOf("notebook") }
    var showLensHelp by rememberSaveable { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<NotebookEntry?>(null) }
    val media =
        rememberMediaLaunchers(
            vm::chooseFolder,
            vm::report,
            vm::prepareCamera,
            vm::cameraResult,
            vm::importPhotos,
        )
    val exportScan = rememberScanExport({ vm.scanUri }, vm::report)
    fun openLink(url: String) {
        runCatching { BrowserActions.open(context, ProblemLinks.normalize(url)) }
            .onFailure { vm.report("Couldn't open this link. Check it or install a browser.") }
    }
    fun goBack() {
        vm.navigate(
            when (vm.page) {
                "settings" -> settingsReturn
                "detail" -> vm.detailOrigin
                else -> "notebook"
            }
        )
    }
    fun requestEdit(entry: NotebookEntry) {
        if (vm.editingId == entry.id && vm.editingRevision == entry.revision) vm.navigate("add")
        else if (vm.draftStarted) pendingEdit = entry else vm.edit(entry)
    }

    BackHandler(enabled = !vm.showWelcome && vm.page != "notebook") { if (!vm.saving) goBack() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    val editorActions =
        EditorActions(
            ::goBack,
            {
                settingsReturn = "add"
                vm.navigate("settings")
            },
            { media.capture("scan") }, media.pickScan, { media.capture("solution") }, media.pickSolutions, media.folder,
            {
                runCatching { BrowserActions.searchText(context, vm.query) }
                    .onFailure {
                        vm.report("Couldn’t open Google. Check that a browser is installed.")
                    }
            },
            { showLensHelp = true }, ::openLink,
        )
    NotebookFrame(
        FrameState(vm.page, vm.showWelcome, settingsReturn, vm.draftStarted),
        snackbar,
        modifier = modifier,
        navigate = { page ->
            if (page == "settings") settingsReturn = vm.page
            vm.navigate(page)
        },
    ) {
        if (vm.showWelcome) {
            WelcomeScreen(vm::completeWelcome, github = { openLink(BrowserActions.GITHUB_URL) })
        } else
            when (vm.page) {
                "notebook" ->
                    NotebookScreen(
                        vm.notebookState(),
                        chooseFolder = media.folder,
                        edit = ::requestEdit,
                        settings = {
                            settingsReturn = "notebook"
                            vm.navigate("settings")
                        },
                    )
                "add" -> AddScreen(vm.editorState(), editorActions)
                "detail" -> SolutionScreen(vm.detailState(), ::goBack, ::openLink, ::requestEdit)
                "progress" -> ProgressScreen(vm.entries, vm::open)
                "settings" ->
                    SettingsScreen(vm.settingsState(), ::goBack, chooseFolder = media.folder)
            }
    }

    pendingEdit?.let { entry ->
        ReplaceDraftDialog(
            entry,
            {
                pendingEdit = null
                vm.edit(entry)
            },
            { pendingEdit = null },
        )
    }
    if (showLensHelp) LensDialog(vm.scanUri, vm::report, exportScan) { showLensHelp = false }
}
