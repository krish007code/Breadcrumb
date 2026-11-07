package dev.breadcrumb.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

data class NotebookEntry(
    val id: String,
    val title: String,
    val url: String,
    val photoUris: List<Uri>,
    val createdAt: Long,
    val site: ProblemSite = ProblemSite.fromUrl(url),
    val directoryUri: Uri? = null,
    val revision: Long = 0,
) {
    val photoUri get() = photoUris.first()
}

data class NotebookListing(val entries: List<NotebookEntry>, val unreadableCount: Int)

class NotebookStore(private val context: Context) {
    private companion object { val fileOperations = Mutex() }
    private val resolver = context.contentResolver
    private data class Snapshot(val metadata: EntryMetadata, val photos: List<DocumentFile>, val marker: DocumentFile)

    private fun root(tree: Uri): DocumentFile = DocumentFile.fromTreeUri(context, tree)
        ?.takeIf { it.exists() && it.isDirectory }
        ?: throw IOException("Your notebook folder is unavailable. Choose it again in Settings.")

    private fun isMetadata(file: DocumentFile): Boolean = file.name == "entry.json" ||
        (file.name.orEmpty().startsWith("revision-") && file.name.orEmpty().endsWith(".json"))

    private fun readLatest(directory: DocumentFile): Snapshot {
        val files = directory.listFiles()
        val legacySite = runCatching { files.firstOrNull { it.name == "site.txt" }?.let { read(it) } }.getOrNull()
        // Incomplete revisions are ignored. The last complete snapshot stays readable.
        return files.filter(::isMetadata).sortedByDescending { it.name }.firstNotNullOfOrNull { marker ->
            runCatching {
                val metadata = EntryMetadata.parse(read(marker), legacySite)
                val photos = metadata.photos.map { name ->
                    files.firstOrNull { it.name == name && it.length() > 0 } ?: throw IOException("Missing solution photo.")
                }
                Snapshot(metadata, photos, marker)
            }.getOrNull()
        } ?: throw IOException("Unable to read this solution's metadata or photos.")
    }

    private fun Snapshot.entry(directory: DocumentFile) = NotebookEntry(metadata.id, metadata.title, metadata.url,
        photos.map(DocumentFile::getUri), metadata.createdAt, metadata.site, directory.uri, metadata.revision)

    suspend fun list(tree: Uri): NotebookListing = withContext(Dispatchers.IO) {
        fileOperations.withLock {
            var unreadable = 0
            val entries = root(tree).listFiles().filter(DocumentFile::isDirectory).mapNotNull { directory ->
                if (directory.listFiles().none(::isMetadata)) return@mapNotNull null
                runCatching { readLatest(directory).entry(directory) }.getOrElse { unreadable++; null }
            }.sortedByDescending(NotebookEntry::createdAt)
            NotebookListing(entries, unreadable)
        }
    }

    suspend fun save(tree: Uri, title: String, url: String, sourcePhotos: List<Uri>, site: ProblemSite): NotebookEntry =
        withContext(Dispatchers.IO) {
            fileOperations.withLock {
                require(sourcePhotos.isNotEmpty()) { "Keep at least one solution photo." }
                val id = UUID.randomUUID().toString()
                val safeTitle = title.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), " ")
                    .replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "Problem" }
                val directory = root(tree).createDirectory("$safeTitle — ${id.take(8)}")
                    ?: throw IOException("Unable to create a problem folder. Check its write access.")
                try {
                    val photos = sourcePhotos.distinct().map { copyPhoto(directory, it) }
                    val metadata = EntryMetadata(id, title, url, photos.map { requireNotNull(it.name) }, System.currentTimeMillis(), site)
                    write(directory, "text/plain", "source.txt", "$title\n$url\n")
                    write(directory, "text/plain", "site.txt", "${site.name}\n")
                    write(directory, "application/json", "entry.json", metadata.toJson())
                    readLatest(directory).entry(directory)
                } catch (error: Exception) { directory.delete(); throw error }
            }
        }

    suspend fun update(tree: Uri, entry: NotebookEntry, title: String, url: String, sourcePhotos: List<Uri>, site: ProblemSite): NotebookEntry =
        withContext(Dispatchers.IO) {
            fileOperations.withLock {
                require(sourcePhotos.isNotEmpty()) { "Keep at least one solution photo." }
                val directory = entryFolder(tree, entry)
                val previous = readLatest(directory)
                require(previous.metadata.revision == entry.revision) { "This solution changed. Reopen it before editing again." }
                val added = mutableListOf<DocumentFile>()
                var marker: DocumentFile? = null
                val committed: Snapshot
                try {
                    val photos = sourcePhotos.distinct().map { uri ->
                        previous.photos.firstOrNull { it.uri == uri } ?: copyPhoto(directory, uri).also(added::add)
                    }
                    val metadata = previous.metadata.copy(title = title, url = url, photos = photos.map { requireNotNull(it.name) },
                        site = site, revision = previous.metadata.revision + 1)
                    val name = "revision-${metadata.revision.toString().padStart(20, '0')}-${UUID.randomUUID()}.json"
                    marker = write(directory, "application/json", name, metadata.toJson())
                    committed = readLatest(directory)
                    require(committed.metadata == metadata) { "Unable to verify the updated solution." }
                } catch (error: Exception) {
                    marker?.delete()
                    added.forEach(DocumentFile::delete)
                    throw error
                }
                // Commit precedes cleanup, so an interrupted edit retains a complete snapshot.
                runCatching {
                    directory.listFiles().filter { isMetadata(it) && it.uri != committed.marker.uri }.forEach { it.delete() }
                    previous.photos.filter { old -> committed.photos.none { it.uri == old.uri } }.forEach { it.delete() }
                }
                runCatching { replaceText(directory, "source.txt", "$title\n$url\n") }
                runCatching { replaceText(directory, "site.txt", "${site.name}\n") }
                committed.entry(directory)
            }
        }

    suspend fun changeSite(tree: Uri, entry: NotebookEntry, site: ProblemSite): NotebookEntry =
        update(tree, entry, entry.title, entry.url, entry.photoUris, site)

    suspend fun delete(tree: Uri, entry: NotebookEntry) = withContext(Dispatchers.IO) {
        fileOperations.withLock {
            if (!entryFolder(tree, entry).delete()) throw IOException("Unable to delete the solution folder. Check its write access and try again.")
        }
    }

    private fun entryFolder(tree: Uri, entry: NotebookEntry): DocumentFile {
        val directory = root(tree).listFiles().firstOrNull { it.isDirectory && it.uri == entry.directoryUri }
            ?: throw IOException("This solution folder is unavailable. Refresh your notebook.")
        require(readLatest(directory).metadata.id == entry.id) { "This solution folder changed. Refresh your notebook." }
        return directory
    }

    private fun copyPhoto(directory: DocumentFile, source: Uri): DocumentFile {
        val mime = resolver.getType(source).orEmpty().ifBlank { "image/jpeg" }
        require(mime.startsWith("image/")) { "Choose a solution photo." }
        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val photo = directory.createFile(mime, "solution-${UUID.randomUUID()}.$extension") ?: throw IOException("Unable to create a solution photo.")
        try {
            val input = resolver.openInputStream(source) ?: throw IOException("A selected photo is unavailable.")
            val copied = input.use { data ->
                val output = resolver.openOutputStream(photo.uri, "w") ?: throw IOException("Unable to write a solution photo.")
                output.use { data.copyTo(it) }
            }
            require(copied > 0) { "A solution photo was empty. Choose another photo." }
            return photo
        } catch (error: Exception) { photo.delete(); throw error }
    }

    private fun read(file: DocumentFile): String = resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
        ?: throw IOException("Unable to read ${file.name}.")

    private fun replaceText(directory: DocumentFile, name: String, contents: String) {
        val file = directory.findFile(name) ?: directory.createFile("text/plain", name) ?: throw IOException("Unable to create $name.")
        val output = resolver.openOutputStream(file.uri, "w") ?: throw IOException("Unable to write $name.")
        output.bufferedWriter().use { it.write(contents) }
    }

    private fun write(directory: DocumentFile, mime: String, name: String, contents: String): DocumentFile {
        val file = directory.createFile(mime, name) ?: throw IOException("Unable to create $name.")
        try {
            val stream = resolver.openOutputStream(file.uri, "w") ?: throw IOException("Unable to write $name.")
            stream.bufferedWriter().use { it.write(contents) }
            return file
        } catch (error: Exception) { file.delete(); throw error }
    }
}
