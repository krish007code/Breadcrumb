package dev.breadcrumb.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val MaxFolderTitle = 80
private const val FolderIdLength = 8
private const val RevisionDigits = 20

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
    val photoUri
        get() = photoUris.first()
}

data class NotebookListing(val entries: List<NotebookEntry>, val unreadableCount: Int)

data class SolutionDraft(
    val title: String,
    val url: String,
    val photos: List<Uri>,
    val site: ProblemSite,
)

class NotebookStore(private val context: Context) {
    private companion object {
        val fileOperations = Mutex()
    }

    private val documents = NotebookDocuments(context)

    private data class Snapshot(
        val metadata: EntryMetadata,
        val photos: List<DocumentFile>,
        val marker: DocumentFile,
    )

    private fun root(tree: Uri): DocumentFile =
        DocumentFile.fromTreeUri(context, tree)?.takeIf { it.exists() && it.isDirectory }
            ?: throw IOException(
                "Your notebook folder is unavailable. Choose it again in Settings."
            )

    private fun isMetadata(file: DocumentFile): Boolean =
        file.name == "entry.json" ||
            (file.name.orEmpty().startsWith("revision-") && file.name.orEmpty().endsWith(".json"))

    private fun readLatest(directory: DocumentFile): Snapshot {
        val files = directory.listFiles()
        val legacySite =
            runCatching { files.firstOrNull { it.name == "site.txt" }?.let { documents.read(it) } }
                .getOrNull()
        // Incomplete revisions are ignored. The last complete snapshot stays readable.
        return files
            .filter(::isMetadata)
            .sortedByDescending { it.name }
            .firstNotNullOfOrNull { marker ->
                runCatching {
                        val metadata = EntryMetadata.parse(documents.read(marker), legacySite)
                        val photos =
                            metadata.photos.map { name ->
                                files.firstOrNull { it.name == name && it.length() > 0 }
                                    ?: throw IOException("Missing solution photo.")
                            }
                        Snapshot(metadata, photos, marker)
                    }
                    .getOrNull()
            } ?: throw IOException("Unable to read this solution's metadata or photos.")
    }

    private fun Snapshot.entry(directory: DocumentFile) =
        NotebookEntry(
            metadata.id,
            metadata.title,
            metadata.url,
            photos.map(DocumentFile::getUri),
            metadata.createdAt,
            metadata.site,
            directory.uri,
            metadata.revision,
        )

    suspend fun list(tree: Uri): NotebookListing =
        withContext(Dispatchers.IO) {
            fileOperations.withLock {
                var unreadable = 0
                val entries =
                    root(tree)
                        .listFiles()
                        .filter(DocumentFile::isDirectory)
                        .mapNotNull { directory ->
                            if (directory.listFiles().none(::isMetadata)) return@mapNotNull null
                            runCatching { readLatest(directory).entry(directory) }
                                .getOrElse {
                                    unreadable++
                                    null
                                }
                        }
                        .sortedByDescending(NotebookEntry::createdAt)
                NotebookListing(entries, unreadable)
            }
        }

    suspend fun save(tree: Uri, draft: SolutionDraft): NotebookEntry =
        withContext(Dispatchers.IO) {
            fileOperations.withLock {
                require(draft.photos.isNotEmpty()) { "Keep at least one solution photo." }
                val id = UUID.randomUUID().toString()
                val safeTitle =
                    draft.title
                        .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(MaxFolderTitle)
                        .ifBlank { "Problem" }
                val directory =
                    root(tree).createDirectory("$safeTitle — ${id.take(FolderIdLength)}")
                        ?: throw IOException(
                            "Unable to create a problem folder. Check its write access."
                        )
                rollbackUnlessComplete({ directory.delete() }) {
                    val photos = draft.photos.distinct().map { documents.copyPhoto(directory, it) }
                    val metadata =
                        EntryMetadata(
                            id,
                            draft.title,
                            draft.url,
                            photos.map { requireNotNull(it.name) },
                            System.currentTimeMillis(),
                            draft.site,
                        )
                    documents.write(
                        directory,
                        "text/plain",
                        "source.txt",
                        "${draft.title}\n${draft.url}\n",
                    )
                    documents.write(directory, "text/plain", "site.txt", "${draft.site.name}\n")
                    documents.write(directory, "application/json", "entry.json", metadata.toJson())
                    readLatest(directory).entry(directory)
                }
            }
        }

    suspend fun update(tree: Uri, entry: NotebookEntry, draft: SolutionDraft): NotebookEntry =
        withContext(Dispatchers.IO) {
            fileOperations.withLock {
                require(draft.photos.isNotEmpty()) { "Keep at least one solution photo." }
                val directory = entryFolder(tree, entry)
                val previous = readLatest(directory)
                require(previous.metadata.revision == entry.revision) {
                    "This solution changed. Reopen it before editing again."
                }
                val added = mutableListOf<DocumentFile>()
                var marker: DocumentFile? = null
                val committed =
                    rollbackUnlessComplete({
                        marker?.delete()
                        added.forEach(DocumentFile::delete)
                    }) {
                        val photos =
                            draft.photos.distinct().map { uri ->
                                previous.photos.firstOrNull { it.uri == uri }
                                    ?: documents.copyPhoto(directory, uri).also(added::add)
                            }
                        val metadata =
                            previous.metadata.copy(
                                title = draft.title,
                                url = draft.url,
                                photos = photos.map { requireNotNull(it.name) },
                                site = draft.site,
                                revision = previous.metadata.revision + 1,
                            )
                        val revision = metadata.revision.toString().padStart(RevisionDigits, '0')
                        val name = "revision-$revision-${UUID.randomUUID()}.json"
                        marker =
                            documents.write(directory, "application/json", name, metadata.toJson())
                        val snapshot = readLatest(directory)
                        require(snapshot.metadata == metadata) {
                            "Unable to verify the updated solution."
                        }
                        snapshot
                    }
                // Commit precedes cleanup, so an interrupted edit retains a complete snapshot.
                runCatching {
                    directory
                        .listFiles()
                        .filter { isMetadata(it) && it.uri != committed.marker.uri }
                        .forEach { it.delete() }
                    previous.photos
                        .filter { old -> committed.photos.none { it.uri == old.uri } }
                        .forEach { it.delete() }
                }
                runCatching {
                    documents.replaceText(directory, "source.txt", "${draft.title}\n${draft.url}\n")
                }
                runCatching { documents.replaceText(directory, "site.txt", "${draft.site.name}\n") }
                committed.entry(directory)
            }
        }

    suspend fun changeSite(tree: Uri, entry: NotebookEntry, site: ProblemSite): NotebookEntry =
        update(tree, entry, SolutionDraft(entry.title, entry.url, entry.photoUris, site))

    suspend fun delete(tree: Uri, entry: NotebookEntry) =
        withContext(Dispatchers.IO) {
            fileOperations.withLock {
                if (!entryFolder(tree, entry).delete())
                    throw IOException(
                        "Unable to delete the solution folder. Check its write access and try again."
                    )
            }
        }

    private fun entryFolder(tree: Uri, entry: NotebookEntry): DocumentFile {
        val directory =
            root(tree).listFiles().firstOrNull { it.isDirectory && it.uri == entry.directoryUri }
                ?: throw IOException("This solution folder is unavailable. Refresh your notebook.")
        require(readLatest(directory).metadata.id == entry.id) {
            "This solution folder changed. Refresh your notebook."
        }
        return directory
    }
}
