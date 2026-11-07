package dev.breadcrumb.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun BreadcrumbApp(vm: NotebookViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val screenState = rememberSaveableStateHolder()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    var settingsReturn by rememberSaveable { mutableStateOf("notebook") }
    var showLensHelp by rememberSaveable { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<NotebookEntry?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                vm.chooseFolder(uri)
            }.onFailure { vm.report("Unable to use this folder. Choose another folder you can write to.") }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), vm::cameraResult)
    val scanPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.importPhoto("scan", it) }
    }
    val solutionPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.importPhotos("solution", uris)
    }
    val exportScan = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { destination ->
        val source = vm.scanUri
        if (destination != null && source != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(source) ?: error("Scan unavailable")
                        input.use { data ->
                            val output = context.contentResolver.openOutputStream(destination, "w") ?: error("File unavailable")
                            output.use { data.copyTo(it) }
                        }
                    }
                    BrowserActions.copyImageSearchPrompt(context)
                    BrowserActions.open(context, "https://lens.google/")
                    vm.report("Select your saved photo in Lens. The request for its question link is copied for Ask about this image.")
                }.onFailure { vm.report("Couldn't open Lens. Your scan can still be selected from the location you saved it.") }
            }
        }
    }

    fun capture(kind: String) {
        runCatching { camera.launch(vm.prepareCamera(kind)) }
            .onFailure { vm.cameraResult(false); vm.report("No camera app is available. Import a photo instead.") }
    }
    fun openLink(url: String) {
        runCatching { BrowserActions.open(context, ProblemLinks.normalize(url)) }
            .onFailure { vm.report("Couldn't open this link. Check it or install a browser.") }
    }
    fun goBack() {
        vm.navigate(when (vm.page) {
            "settings" -> settingsReturn
            "detail" -> vm.detailOrigin
            else -> "notebook"
        })
    }
    fun requestEdit(entry: NotebookEntry) {
        if (vm.editingId == entry.id && vm.editingRevision == entry.revision) vm.navigate("add")
        else if (vm.draftStarted) pendingEdit = entry
        else vm.edit(entry)
    }

    BackHandler(enabled = !vm.showWelcome && vm.page != "notebook") { if (!vm.saving) goBack() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!vm.showWelcome && (vm.page == "notebook" || vm.page == "progress" || (vm.page == "settings" && settingsReturn != "add"))) {
                NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = vm.page == "notebook", onClick = { vm.navigate("notebook") },
                        icon = { Icon(Icons.Outlined.Home, null) }, label = { Text("Notebook") })
                    NavigationBarItem(selected = vm.page == "progress", onClick = { vm.navigate("progress") },
                        icon = { Icon(Icons.Outlined.BarChart, null) }, label = { Text("Progress") })
                    NavigationBarItem(selected = vm.page == "settings", onClick = {
                        if (vm.page != "settings") settingsReturn = vm.page
                        vm.navigate("settings")
                    }, icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Settings") })
                }
            }
        },
        floatingActionButton = {
            if (!vm.showWelcome && vm.page == "notebook") {
                ExtendedFloatingActionButton(
                    onClick = { vm.navigate("add") }, containerColor = Ink, contentColor = Color.White,
                    shape = RoundedCornerShape(28.dp), icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(if (vm.draftStarted) "Continue draft" else "New solution") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                screenState.SaveableStateProvider(if (vm.showWelcome) "welcome" else vm.page) {
                    if (vm.showWelcome) {
                        WelcomeScreen(vm::completeWelcome, github = { openLink(BrowserActions.GITHUB_URL) })
                    } else when (vm.page) {
                    "notebook" -> NotebookScreen(vm, chooseFolder = { folderPicker.launch(null) }, edit = ::requestEdit,
                        settings = { settingsReturn = "notebook"; vm.navigate("settings") })
                    "add" -> AddScreen(vm, back = ::goBack,
                        settings = { settingsReturn = "add"; vm.navigate("settings") },
                        scanCamera = { capture("scan") },
                        scanImport = { scanPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        solutionCamera = { capture("solution") },
                        solutionImport = { solutionPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        chooseFolder = { folderPicker.launch(null) },
                        googleText = {
                            runCatching { BrowserActions.searchText(context, vm.query) }
                                .onFailure { vm.report("Couldn't open Google. Check that a browser is installed.") }
                        },
                        googleImage = { showLensHelp = true }, openLink = ::openLink)
                    "detail" -> SolutionScreen(vm, ::goBack, ::openLink, ::requestEdit)
                    "progress" -> ProgressScreen(vm.entries, vm::open)
                    "settings" -> SettingsScreen(vm, ::goBack, chooseFolder = { folderPicker.launch(null) })
                  }
                }
            }
        }
    }

    pendingEdit?.let { entry ->
        AlertDialog(onDismissRequest = { pendingEdit = null }, title = { Text("Replace your current draft?") },
            text = { Text("Open ${entry.title} for editing and discard the unfinished draft?") },
            confirmButton = { TextButton(onClick = { pendingEdit = null; vm.edit(entry) }) { Text("Discard draft and edit") } },
            dismissButton = { TextButton(onClick = { pendingEdit = null }) { Text("Keep draft") } })
    }

    if (showLensHelp) {
        AlertDialog(
            onDismissRequest = { showLensHelp = false },
            title = { Text("Search this photo") },
            text = { Text("The photo is sent with ‘Give me the original link for this question.’ The request is also copied so you can paste it into Ask about this image if Google leaves the text out. Share or paste the question link back here.") },
            confirmButton = {
                TextButton(onClick = {
                    showLensHelp = false
                    vm.scanUri?.let { photo ->
                        runCatching { BrowserActions.searchImage(context, photo) }
                            .onFailure { vm.report("No image app accepted the scan. Use Search with this photo again and choose Save and open Lens.") }
                    }
                }) { Text("Open with Google") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLensHelp = false
                    val scan = vm.scanUri ?: return@TextButton
                    val extension = when (context.contentResolver.getType(scan)) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/heic", "image/heif" -> "heic"
                        else -> "jpg"
                    }
                    exportScan.launch("Breadcrumb-scan.$extension")
                }) { Text("Save and open Lens") }
            },
        )
    }
}

@Composable
internal fun Header(title: String, back: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        else {
            Image(painterResource(R.drawable.ic_breadcrumb), null, Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        action?.invoke()
    }
}

@Composable
private fun NotebookScreen(vm: NotebookViewModel, chooseFolder: () -> Unit, settings: () -> Unit, edit: (NotebookEntry) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var siteFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var siteMenu by remember { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    vm.entries.firstOrNull { it.id == deleteId }?.let { entry ->
        DeleteSolutionDialog(entry, { deleteId = null; vm.deleteEntry(entry) }, { deleteId = null })
    }
    Header("Breadcrumb", action = {
        IconButton(onClick = settings) { Icon(Icons.Outlined.Settings, "Settings") }
    })
    OutlinedTextField(
        value = filter, onValueChange = { filter = it }, placeholder = { Text("Search problem titles") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp), singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, unfocusedBorderColor = Border),
    )
    Box(Modifier.padding(horizontal = 24.dp)) {
        TextButton(onClick = { siteMenu = true }) {
            Text(ProblemSite.entries.firstOrNull { it.name == siteFilter }?.label ?: "All sites")
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded = siteMenu, onDismissRequest = { siteMenu = false }) {
            DropdownMenuItem(text = { Text("All sites") }, onClick = { siteFilter = null; siteMenu = false })
            ProblemSite.entries.forEach { site ->
                DropdownMenuItem(text = { Text(site.label) }, onClick = { siteFilter = site.name; siteMenu = false })
            }
        }
    }
    when {
        vm.notebookUri == null -> EmptyPanel(Icons.Outlined.FolderOpen, "A place for your solutions",
            "Choose a folder on your phone. Breadcrumb will keep each problem link and your handwritten solution together there.",
            "Choose notebook folder", chooseFolder)
        vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink) }
        vm.folderError != null -> EmptyPanel(Icons.Outlined.FolderOpen, "Open your notebook again",
            vm.folderError.orEmpty(), "Choose folder", chooseFolder)
        vm.entries.isEmpty() -> EmptyPanel(Icons.Outlined.EditNote, "Your first breadcrumb",
            "Add a problem link and a photo of your solution. Come back whenever you want to revise it.", "Add a solution", { vm.navigate("add") })
        else -> {
            val visible = vm.entries.filter { ProblemLinks.matches(it.title, filter) && (siteFilter == null || it.site.name == siteFilter) }
            Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${visible.size} ${if (visible.size == 1) "solution" else "solutions"}", Modifier.weight(1f), color = Muted)
                IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, "Refresh notebook") }
            }
            if (visible.isEmpty()) {
                EmptyPanel(Icons.Outlined.Search, "No matching titles", "Try a different word from the problem title.")
            } else LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = NotebookEntry::id) { entry ->
                    SolutionRow(entry, onEdit = { edit(entry) }, onDelete = { deleteId = entry.id }) { vm.open(entry) }
                }
            }
        }
    }
}

@Composable
private fun EmptyPanel(icon: ImageVector, title: String, text: String, button: String? = null, action: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(68.dp).background(Amber, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(32.dp)) }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(text, color = Muted)
        if (button != null) Button(onClick = action, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) { Text(button) }
    }
}

@Composable
private fun AddScreen(
    vm: NotebookViewModel, back: () -> Unit, settings: () -> Unit,
    scanCamera: () -> Unit, scanImport: () -> Unit,
    solutionCamera: () -> Unit, solutionImport: () -> Unit,
    chooseFolder: () -> Unit, googleText: () -> Unit, googleImage: () -> Unit, openLink: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val enabled = !vm.saving && !vm.reading
    var review by rememberSaveable { mutableStateOf(false) }
    if (review) PhotoReviewDialog(vm.solutionUris, confirm = { photos -> review = false; vm.save(photos) }, dismiss = { review = false })
    Header(if (vm.editingId == null) "New solution" else "Edit solution", back = { if (!vm.saving) back() }, action = {
        IconButton(onClick = settings, enabled = !vm.saving) { Icon(Icons.Outlined.Settings, "Settings") }
    })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Panel {
            Text("Find the problem", style = MaterialTheme.typography.titleMedium)
            Text("Scan a screen or paste its link below.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Scan", Icons.Outlined.PhotoCamera, scanCamera, modifier = Modifier.weight(1f), enabled = enabled)
                ActionButton("Screenshot", Icons.Outlined.Image, scanImport, modifier = Modifier.weight(1f), enabled = enabled)
            }
            if (vm.scanUri == null) {
                TextButton(onClick = googleText, enabled = enabled) { Text("Search Google manually") }
            }
            vm.scanUri?.let { scan ->
                AsyncImage(scan, "Captured question", Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(14.dp)).background(Sand), contentScale = ContentScale.Fit)
                if (vm.reading) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(vm.query, vm::updateQuery, Modifier.fillMaxWidth(), label = { Text("Words from your scan") },
                    minLines = 2, maxLines = 4, enabled = enabled, shape = RoundedCornerShape(16.dp))
                ActionButton("Search Google with text", Icons.Outlined.Search, googleText, enabled = enabled)
                ActionButton("Search with this photo", Icons.Outlined.ImageSearch, googleImage, enabled = enabled)
                if (vm.hasSearchKey) TextButton(onClick = vm::findMatches, enabled = enabled && !vm.searching && vm.query.isNotBlank()) {
                    Text(if (vm.searching) "Finding matches…" else "Find matching problem")
                }
            }
            if (vm.status.isNotBlank()) Text(vm.status, color = Muted, style = MaterialTheme.typography.bodyMedium)
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.matches.forEach { match ->
                Surface(shape = RoundedCornerShape(16.dp), color = Sand) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(match.title, style = MaterialTheme.typography.titleMedium)
                        Text(match.url, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.select(match) }) { Text("Use this problem") }
                            IconButton(onClick = { openLink(match.url) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Check problem in browser") }
                        }
                    }
                }
            }
        }
        Panel {
            OutlinedTextField(vm.url, vm::updateUrl, Modifier.fillMaxWidth(), label = { Text("Problem link") },
                placeholder = { Text("https://leetcode.com/problems/…") },
                trailingIcon = { IconButton(enabled = enabled, onClick = {
                    val text = clipboard.getText()?.text.orEmpty()
                    vm.updateUrl(ProblemLinks.extractSharedLink(text) ?: text)
                }) { Icon(Icons.Outlined.ContentPaste, "Paste problem link") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), enabled = !vm.saving,
                shape = RoundedCornerShape(16.dp), minLines = 1, maxLines = 3)
            TextButton(onClick = vm::cleanUrl, enabled = enabled && vm.url.isNotBlank()) {
                Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clean URL")
            }
            OutlinedTextField(vm.title, vm::updateTitle, Modifier.fillMaxWidth(), label = { Text("Problem title") },
                enabled = !vm.saving, shape = RoundedCornerShape(16.dp), maxLines = 3)
            SiteDropdown(vm.site, vm::updateSite, enabled = !vm.saving)
        }
        Panel {
            Text("Solution photos (${vm.solutionUris.size})", style = MaterialTheme.typography.titleMedium)
            if (vm.solutionUris.isNotEmpty()) {
                Text("Add more pages or remove photos before saving.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                vm.solutionUris.forEachIndexed { index, photo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(photo, "Solution photo ${index + 1}", Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(16.dp)).background(Sand), contentScale = ContentScale.Fit)
                        IconButton(onClick = { vm.removePhoto(photo) }, enabled = enabled) { Icon(Icons.Outlined.Close, "Remove photo ${index + 1}") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Take photo", Icons.Outlined.PhotoCamera, solutionCamera, modifier = Modifier.weight(1f), enabled = enabled)
                ActionButton("Add photos", Icons.Outlined.Image, solutionImport, modifier = Modifier.weight(1f), enabled = enabled)
            }
        }
        if (vm.notebookUri == null) ActionButton("Choose notebook folder", Icons.Outlined.FolderOpen, chooseFolder, enabled = enabled)
        Button(onClick = { review = true }, enabled = enabled && !vm.loading && vm.title.isNotBlank() && vm.url.isNotBlank() && vm.solutionUris.isNotEmpty() && vm.notebookUri != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(28.dp)) {
            if (vm.saving) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
            Text(if (vm.saving) "Saving solution…" else "Review and save")
        }
    }
}

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SolutionRow(entry: NotebookEntry, onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, action: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
    Surface(modifier = Modifier.clip(RoundedCornerShape(22.dp)).combinedClickable(onClick = action,
        onLongClickLabel = "Edit or delete solution", onLongClick = if (onEdit != null || onDelete != null) ({ menu = true }) else null),
        shape = RoundedCornerShape(22.dp), color = Color.White) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(entry.photoUri, "Solution thumbnail", Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Sand), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(entry.site.let { if (it == ProblemSite.OTHER) Uri.parse(entry.url).host.orEmpty() else it.label },
                    color = Muted, style = MaterialTheme.typography.bodyMedium)
                if (entry.photoUris.size > 1) Text("${entry.photoUris.size} photos", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
        }
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        if (onEdit != null) DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
        if (onDelete != null) DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) }, onClick = { menu = false; onDelete() })
    }
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, action: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = action, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun SolutionScreen(vm: NotebookViewModel, back: () -> Unit, openLink: (String) -> Unit, edit: (NotebookEntry) -> Unit) {
    var fullscreen by rememberSaveable { mutableIntStateOf(-1) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Header("Your solution", back = { if (!vm.saving) back() }, action = {
        IconButton(onClick = { vm.selectedEntry?.let(edit) }, enabled = !vm.saving && vm.selectedEntry != null) {
            Icon(Icons.Outlined.Edit, "Edit solution")
        }
        IconButton(onClick = { confirmDelete = true }, enabled = !vm.saving && vm.selectedEntry != null) {
            Icon(Icons.Outlined.DeleteOutline, "Delete solution")
        }
    })
    val entry = vm.selectedEntry
    if (entry == null) {
        if (vm.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else EmptyPanel(Icons.Outlined.FolderOpen, "Solution unavailable", "Refresh the notebook or choose its folder again.", "Back to notebook", back)
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(entry.title, style = MaterialTheme.typography.headlineMedium)
        SiteDropdown(entry.site, { vm.changeEntrySite(entry, it) }, enabled = !vm.saving)
        ActionButton("Open question", Icons.AutoMirrored.Outlined.OpenInNew, { openLink(entry.url) })
        val pager = rememberPagerState(pageCount = { entry.photoUris.size })
        Text("Photo ${pager.currentPage + 1} of ${entry.photoUris.size}", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                AsyncImage(entry.photoUris[index], "Solution photo ${index + 1}. Tap to enlarge.", Modifier.fillMaxSize().clickable { fullscreen = index }, contentScale = ContentScale.Fit)
            }
            Icon(Icons.Outlined.ZoomIn, "Enlarge solution", Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Paper, CircleShape).padding(10.dp))
        }
    }
    if (confirmDelete) DeleteSolutionDialog(entry, { confirmDelete = false; vm.deleteEntry(entry) }, { confirmDelete = false })
    if (fullscreen >= 0) PhotoViewer(entry.photoUris, fullscreen.coerceAtMost(entry.photoUris.lastIndex)) { fullscreen = -1 }
}

@Composable
private fun DeleteSolutionDialog(entry: NotebookEntry, confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Delete this solution?") },
        text = { Text("${entry.title} and all ${entry.photoUris.size} solution photos will be removed from your notebook folder. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = confirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Keep solution") } },
    )
}

@Composable
private fun PhotoViewer(photos: List<Uri>, initialIndex: Int, close: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(initialIndex) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      key(photos[index]) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale == 1f) Offset.Zero else offset + pan
            }
        }) {
            AsyncImage(photos[index], "Your solution. Pinch to zoom and drag to pan.", Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            }, contentScale = ContentScale.Fit)
            IconButton(onClick = close, Modifier.align(Alignment.TopEnd).padding(16.dp).background(Ink, CircleShape)) {
                Icon(Icons.Outlined.Close, "Close enlarged photo", tint = Color.White)
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Ink, RoundedCornerShape(24.dp)), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { index-- }, enabled = index > 0) { Icon(Icons.Outlined.ChevronLeft, "Previous photo", tint = Color.White) }
                Text("${index + 1} / ${photos.size}", color = Color.White)
                IconButton(onClick = { index++ }, enabled = index < photos.lastIndex) { Icon(Icons.Outlined.ChevronRight, "Next photo", tint = Color.White) }
            }
        }
      }
    }
}

@Composable
private fun PhotoReviewDialog(photos: List<Uri>, confirm: (List<Uri>) -> Unit, dismiss: () -> Unit) {
    var kept by rememberSaveable(photos) { mutableStateOf(ArrayList(photos.map(Uri::toString))) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Choose photos to keep") },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(photos, key = Uri::toString) { photo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(modifier = Modifier.semantics { contentDescription = "Keep solution photo ${photos.indexOf(photo) + 1}" }, checked = photo.toString() in kept, onCheckedChange = { selected ->
                            kept = ArrayList(if (selected) kept + photo.toString() else kept - photo.toString())
                        })
                        AsyncImage(photo, "Solution photo ${photos.indexOf(photo) + 1}", Modifier.weight(1f).height(130.dp).clip(RoundedCornerShape(12.dp)).background(Sand), contentScale = ContentScale.Fit)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { confirm(photos.filter { it.toString() in kept }) }, enabled = kept.isNotEmpty()) {
            Text("Save ${kept.size} ${if (kept.size == 1) "photo" else "photos"}")
        } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Back") } })
}

@Composable
private fun SettingsScreen(vm: NotebookViewModel, back: () -> Unit, chooseFolder: () -> Unit) {
    var key by remember { mutableStateOf("") }
    Header("Settings", back)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Panel {
            Text("Notebook folder", style = MaterialTheme.typography.titleMedium)
            Text(if (vm.notebookUri == null) "Choose where to keep your solutions." else vm.notebookName, color = Muted)
            Text("Copy this entire folder to your hard disk whenever you want a backup. Choose the same folder to reopen your notebook after reinstalling.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            ActionButton(if (vm.notebookUri == null) "Choose folder" else "Change or reopen folder", Icons.Outlined.FolderOpen, chooseFolder, enabled = !vm.saving && !vm.loading)
        }
        Panel {
            Text("Automatic scan lookup", style = MaterialTheme.typography.titleMedium)
            Text("Add your TinyFish Search API key to get problem-link suggestions after scanning. Recognized words are sent to TinyFish. Google search and pasting links work without a key.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            if (vm.hasSearchKey) Text("A search key is saved on this phone.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(if (vm.hasSearchKey) "Replace API key" else "TinyFish API key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(onClick = { vm.saveSearchKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
            if (vm.hasSearchKey) TextButton(onClick = { vm.saveSearchKey("") }) { Text("Remove key") }
        }
        TextButton(onClick = vm::showTutorial) { Text("View welcome tutorial") }
    }
}

@Composable
private fun SiteDropdown(site: ProblemSite, select: (ProblemSite) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Site", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(site.label, Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, "Choose site")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ProblemSite.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { select(option); expanded = false })
                }
            }
        }
    }
}
