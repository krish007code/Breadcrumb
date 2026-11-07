package dev.breadcrumb.app

import android.app.Application
import android.net.Uri
import android.util.Log
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
    var showWelcome by mutableStateOf(!settings.welcomeCompleted)
        private set
    var site by mutableStateOf(ProblemSite.fromStored(saved.get<String>("site"), saved.get<String>("url").orEmpty()))
        private set
    private var manualSite = saved.get<Boolean>("manualSite") ?: false
    var url by mutableStateOf(saved.get<String>("url").orEmpty())
        private set
    var query by mutableStateOf(saved.get<String>("query").orEmpty())
        private set
    var scanUri by mutableStateOf(saved.get<String>("scan")?.let(Uri::parse))
        private set
    var solutionUris by mutableStateOf((saved.get<ArrayList<String>>("solutions")
        ?: ArrayList(listOfNotNull(saved.get<String>("solution")))).map(Uri::parse))
        private set
    var editingId by mutableStateOf(saved.get<String>("editingId"))
        private set
    val editingRevision get() = saved.get<Long>("editingRevision")
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
    val draftStarted get() = title.isNotBlank() || url.isNotBlank() || scanUri != null || solutionUris.isNotEmpty()

    init { refresh() }

    fun navigate(destination: String) {
        if (saving) return
        page = destination
        saved["page"] = destination
    }

    fun updateTitle(value: String) { title = value; saved["title"] = value }
    fun cleanUrl() {
        runCatching { ProblemLinks.clean(url) }.onSuccess { updateUrl(it); report("URL cleaned.") }
            .onFailure { report(it.message ?: "Check the problem link first.") }
    }
    private fun setPhotos(photos: List<Uri>) {
        solutionUris = photos.distinct()
        saved["solutions"] = ArrayList(solutionUris.map(Uri::toString))
        saved.remove<String>("solution")
    }
    fun removePhoto(uri: Uri) { if (!saving && !reading) setPhotos(solutionUris - uri) }
    fun edit(entry: NotebookEntry) {
        if (saving || reading) return
        saved["detailOrigin"] = if (page == "detail") detailOrigin else page
        clearDraft()
        editingId = entry.id
        saved["editingId"] = entry.id
        saved["editingRevision"] = entry.revision
        updateUrl(entry.url)
        updateTitle(entry.title)
        updateSite(entry.site)
        setPhotos(entry.photoUris)
        navigate("add")
    }
    fun completeWelcome() { settings.welcomeCompleted = true; showWelcome = false }
    fun showTutorial() { showWelcome = true }
    fun updateSite(value: ProblemSite) {
        site = value
        manualSite = true
        saved["site"] = value.name
        saved["manualSite"] = true
    }
    fun updateUrl(value: String) {
        val oldSuggestion = ProblemLinks.suggestedTitle(url)
        url = value
        saved["url"] = value
        if (!manualSite) { site = ProblemSite.fromUrl(value); saved["site"] = site.name }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
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

    fun changeEntrySite(entry: NotebookEntry, value: ProblemSite) {
        val tree = notebookUri ?: return
        if (saving || entry.site == value) return
        saving = true
        loadJob?.cancel()
        viewModelScope.launch {
            try {
                val updated = withContext(NonCancellable) { store.changeSite(tree, entry, value) }
                entries = entries.map { if (it.id == entry.id) updated else it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                report(error.message ?: "Unable to save this category.")
            } finally { saving = false }
        }
    }

    fun deleteEntry(entry: NotebookEntry) {
        val tree = notebookUri ?: return
        if (saving) return
        saving = true
        loadJob?.cancel()
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { store.delete(tree, entry) }
                entries = entries.filterNot { it.id == entry.id }
                if (editingId == entry.id) clearDraft()
                if (page == "detail" && selectedId == entry.id) {
                    selectedId = null
                    saved.remove<String>("selectedId")
                    page = detailOrigin
                    saved["page"] = page
                }
                report("Solution deleted.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                report(error.message ?: "Unable to delete this solution.")
            } finally { saving = false; refresh() }
        }
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

    fun importPhoto(kind: String, uri: Uri) = importPhotos(kind, listOf(uri))

    fun importPhotos(kind: String, uris: List<Uri>) {
        if (saving || reading) return
        if (uris.isEmpty()) return
        reading = true
        navigate("add")
        viewModelScope.launch {
            val imported = mutableListOf<Uri>()
            try {
                withContext(Dispatchers.IO) {
                  uris.forEach { uri ->
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
                        imported.add(fileUri(target))
                    } catch (error: Exception) { target.delete(); throw error }
                  }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                report(error.message ?: "Unable to import this photo.")
            } finally {
                reading = false
                if (kind == "solution") setPhotos(solutionUris + imported)
                else imported.lastOrNull()?.let { acceptImage(kind, it) }
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
            setPhotos(solutionUris + uri)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("Breadcrumb", "Unable to read the question scan.", error)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == searchGeneration) status = error.message ?: "Search failed. Try Google or paste a link."
            } finally { if (generation == searchGeneration) searching = false }
        }
    }

    fun saveSearchKey(key: String) {
        runCatching { settings.setSearchKey(key) }
            .onSuccess { hasSearchKey = key.isNotBlank(); report(if (key.isBlank()) "Search key removed." else "Search key saved on this phone.") }
            .onFailure { report("Unable to save the search key on this phone.") }
    }

    fun save(photos: List<Uri>) {
        if (saving || reading || loading) return
        val tree = notebookUri ?: run { report("Choose your notebook folder first."); return }
        val cleanedTitle = title.trim()
        if (cleanedTitle.isBlank()) { report("Give this problem a title."); return }
        val normalizedUrl = runCatching { ProblemLinks.normalize(url) }.getOrElse { report(it.message.orEmpty()); return }
        val selectedPhotos = photos.distinct()
        if (selectedPhotos.isEmpty() || !solutionUris.containsAll(selectedPhotos)) { report("Keep at least one solution photo."); return }
        val original = editingId?.let { id -> entries.firstOrNull { it.id == id } }
        if (editingId != null && original == null) { report("The original entry is unavailable. Reopen its notebook folder."); return }
        val selectedSite = site
        saving = true
        viewModelScope.launch {
            try {
                // Finish an in-progress file write even if navigation or activity lifetime changes.
                val entry = withContext(NonCancellable) {
                    if (original == null) store.save(tree, cleanedTitle, normalizedUrl, selectedPhotos, selectedSite)
                    else store.update(tree, original.copy(revision = saved.get<Long>("editingRevision") ?: original.revision),
                        cleanedTitle, normalizedUrl, selectedPhotos, selectedSite)
                }
                entries = (entries.filterNot { it.id == entry.id } + entry).sortedByDescending(NotebookEntry::createdAt)
                clearDraft()
                selectedId = entry.id
                saved["selectedId"] = entry.id
                page = if (original == null) "notebook" else "detail"
                saved["page"] = page
                report(if (original == null) "Solution saved." else "Solution updated.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
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
        manualSite = false
        site = ProblemSite.OTHER
        saved.remove<String>("site")
        saved.remove<Boolean>("manualSite")
        scanUri = null
        setPhotos(emptyList())
        editingId = null
        saved.remove<String>("editingId")
        saved.remove<Long>("editingRevision")
        saved.remove<String>("scan")
        saved.remove<String>("solution")
        status = ""
        reading = false
        searching = false
        // Photos live here only until the entry is safely written to the notebook.
        File(context.filesDir, "drafts").listFiles()?.forEach { it.delete() }
    }
}
