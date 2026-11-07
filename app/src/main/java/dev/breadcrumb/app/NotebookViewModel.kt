package dev.breadcrumb.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class NotebookViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val settings = LocalSettings(application)
    private val store = NotebookStore(application)
    private val search = ProblemSearch()
    private var scanJob: Job? = null
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var searchGeneration = 0

    var page by mutableStateOf(saved.get<String>("page") ?: "notebook")
        private set
    var title by mutableStateOf(saved.get<String>("title").orEmpty())
        private set
    var url by mutableStateOf(saved.get<String>("url").orEmpty())
        private set
    var query by mutableStateOf(saved.get<String>("query").orEmpty())
        private set
    var scanUri by mutableStateOf(saved.get<String>("scan")?.let(Uri::parse))
        private set
    var solutionUri by mutableStateOf(saved.get<String>("solution")?.let(Uri::parse))
        private set
    var entries by mutableStateOf<List<NotebookEntry>>(emptyList())
        private set
    var matches by mutableStateOf<List<ProblemMatch>>(emptyList())
        private set
    var notebookUri by mutableStateOf(settings.notebookUri)
        private set
    var notebookName by mutableStateOf(settings.notebookName)
        private set
    var hasSearchKey by mutableStateOf(settings.searchKey().isNotBlank())
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var reading by mutableStateOf(false)
        private set
    var searching by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var folderError by mutableStateOf<String?>(null)
        private set
    var selectedId by mutableStateOf(saved.get<String>("selectedId"))
        private set
    val detailOrigin get() = saved.get<String>("detailOrigin") ?: "notebook"
    val selectedEntry get() = entries.firstOrNull { it.id == selectedId }
    val draftStarted get() = title.isNotBlank() || url.isNotBlank() || scanUri != null || solutionUri != null

    init { refresh() }

    fun navigate(destination: String) {
        if (saving) return
        page = destination
        saved["page"] = destination
    }

    fun updateTitle(value: String) { title = value; saved["title"] = value }
    fun updateUrl(value: String) {
        val oldSuggestion = ProblemLinks.suggestedTitle(url)
        url = value
        saved["url"] = value
        if (title.isBlank() || title == oldSuggestion) updateTitle(ProblemLinks.suggestedTitle(value))
    }
    fun updateQuery(value: String) {
        searchGeneration++
        searchJob?.cancel()
        searching = false
        matches = emptyList()
        query = value
        saved["query"] = value
    }
    fun dismissMessage() { message = null }
    fun report(value: String) { message = value }

    fun chooseFolder(uri: Uri) {
        if (saving) return
        settings.notebookUri = uri
        notebookUri = uri
        entries = emptyList()
        folderError = null
        notebookName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Notebook"
        settings.notebookName = notebookName
        refresh()
    }

    fun refresh() {
        val tree = notebookUri ?: return
        if (saving) return
        loadJob?.cancel()
        val generation = ++loadGeneration
        loading = true
        loadJob = viewModelScope.launch {
            try {
                val listing = store.list(tree)
                if (generation != loadGeneration) return@launch
                entries = listing.entries
                folderError = null
                if (listing.unreadableCount > 0) {
                    message = "${listing.unreadableCount} saved entries couldn't be read. Their files are still in your notebook folder."
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation != loadGeneration) return@launch
                entries = emptyList()
                folderError = error.message ?: "Unable to open your notebook. Choose its folder again."
            } finally { if (generation == loadGeneration) loading = false }
        }
    }

    fun open(entry: NotebookEntry) {
        saved["detailOrigin"] = page
        selectedId = entry.id
        saved["selectedId"] = entry.id
        navigate("detail")
    }

    fun select(match: ProblemMatch) {
        updateUrl(match.url)
        updateTitle(match.title)
        matches = emptyList()
        status = "Problem selected. Add your solution photo below."
    }

    fun receiveLink(text: String) {
        if (saving) { report("Finish saving this solution before adding another link."); return }
        val shared = ProblemLinks.extractSharedLink(text)
        if (shared == null) { report("Share a problem page link from your browser."); return }
        updateUrl(shared)
        navigate("add")
    }

    fun prepareCamera(kind: String): Uri {
        val file = draftFile("jpg")
        val uri = fileUri(file)
        saved["pendingCamera"] = uri.toString()
        saved["pendingKind"] = kind
        return uri
    }

    fun cameraResult(success: Boolean) {
        val uri = saved.get<String>("pendingCamera")?.let(Uri::parse)
        val kind = saved.get<String>("pendingKind")
        saved.remove<String>("pendingCamera")
        saved.remove<String>("pendingKind")
        if (success && uri != null && kind != null) acceptImage(kind, uri)
    }

    fun importPhoto(kind: String, uri: Uri) {
        if (saving || reading) return
        reading = true
        navigate("add")
        viewModelScope.launch {
            try {
                val local = withContext(Dispatchers.IO) {
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    require(mime.startsWith("image/")) { "Choose an image file." }
                    val extension = when (mime) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/heic", "image/heif" -> "heic"
                        else -> "jpg"
                    }
                    val target = draftFile(extension)
                    try {
                        val source = context.contentResolver.openInputStream(uri) ?: throw IOException("Unable to open this photo.")
                        val bytes = source.use { input -> target.outputStream().use { input.copyTo(it) } }
                        require(bytes > 0) { "This photo is empty." }
                        fileUri(target)
                    } catch (error: Exception) { target.delete(); throw error }
                }
                reading = false
                acceptImage(kind, local)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                report(error.message ?: "Unable to import this photo.")
                reading = false
            }
        }
    }

    private fun draftFile(extension: String): File {
        val folder = File(context.filesDir, "drafts").apply { mkdirs() }
        return File(folder, "${UUID.randomUUID()}.$extension")
    }

    private fun fileUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun acceptImage(kind: String, uri: Uri) {
        navigate("add")
        if (kind == "solution") {
            solutionUri = uri
            saved["solution"] = uri.toString()
        } else {
            scanUri = uri
            saved["scan"] = uri.toString()
            readScan(uri)
        }
    }

    private fun readScan(uri: Uri) {
        scanJob?.cancel()
        searchJob?.cancel()
        searching = false
        matches = emptyList()
        reading = true
        status = "Reading your scan…"
        scanJob = viewModelScope.launch {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val input = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
                val text = recognizer.process(input).await().text
                updateQuery(ProblemLinks.scanQuery(text))
                status = if (query.isBlank()) "Couldn't read this photo. Try Google Lens with the same image, or paste a link."
                    else "Scan read. Check the search words or try Google Lens."
                if (query.isNotBlank() && hasSearchKey) findMatches()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                status = "Couldn't read this photo. Try Google Lens with the same image, or paste a link."
            } finally { recognizer.close(); reading = false }
        }
    }

    fun findMatches() {
        if (query.isBlank() || searching) return
        if (!hasSearchKey) {
            status = "Use Google below, or add your TinyFish search key in Settings for automatic suggestions."
            return
        }
        searchJob?.cancel()
        searching = true
        val generation = ++searchGeneration
        status = "Looking for the original problem…"
        matches = emptyList()
        val requestedQuery = query
        searchJob = viewModelScope.launch {
            try {
                val found = search.find(requestedQuery, settings.searchKey())
                if (generation == searchGeneration && requestedQuery == query) {
                    matches = found
                    status = if (found.isEmpty()) "Couldn't find a matching problem. Try Google or paste its link."
                        else "Choose the correct problem below."
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == searchGeneration) status = error.message ?: "Search failed. Try Google or paste a link."
            } finally { if (generation == searchGeneration) searching = false }
        }
    }

    fun saveSearchKey(key: String) {
        runCatching { settings.setSearchKey(key) }
            .onSuccess { hasSearchKey = key.isNotBlank(); report(if (key.isBlank()) "Search key removed." else "Search key saved on this phone.") }
            .onFailure { report("Unable to save the search key on this phone.") }
    }

    fun save() {
        if (saving || reading || loading) return
        val tree = notebookUri ?: run { report("Choose your notebook folder first."); return }
        val cleanedTitle = title.trim()
        if (cleanedTitle.isBlank()) { report("Give this problem a title."); return }
        val normalizedUrl = runCatching { ProblemLinks.normalize(url) }.getOrElse { report(it.message.orEmpty()); return }
        val photo = solutionUri ?: run { report("Add a photo of your solution."); return }
        saving = true
        viewModelScope.launch {
            try {
                // Finish an in-progress file write even if navigation or activity lifetime changes.
                val entry = withContext(NonCancellable) { store.save(tree, cleanedTitle, normalizedUrl, photo) }
                entries = (entries + entry).sortedByDescending(NotebookEntry::createdAt)
                clearDraft()
                page = "notebook"
                saved["page"] = page
                report("Solution saved.")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                report(error.message ?: "Couldn't save. Your draft is still here. Try again.")
            } finally { saving = false }
        }
    }

    private fun clearDraft() {
        scanJob?.cancel()
        searchJob?.cancel()
        updateTitle("")
        updateUrl("")
        updateQuery("")
        scanUri = null
        solutionUri = null
        saved.remove<String>("scan")
        saved.remove<String>("solution")
        status = ""
        reading = false
        searching = false
        // Photos live here only until the entry is safely written to the notebook.
        File(context.filesDir, "drafts").listFiles()?.forEach { it.delete() }
    }
}
