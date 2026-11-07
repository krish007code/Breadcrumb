package dev.breadcrumb.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

data class NotebookEntry(
    val id: String,
    val title: String,
    val url: String,
    val photoUri: Uri,
    val createdAt: Long,
    val site: ProblemSite = ProblemSite.fromUrl(url),
    val directoryUri: Uri? = null,
)

data class NotebookListing(val entries: List<NotebookEntry>, val unreadableCount: Int)

class NotebookStore(private val context: Context) {
    private val resolver = context.contentResolver

    private fun root(tree: Uri): DocumentFile = DocumentFile.fromTreeUri(context, tree)
        ?.takeIf { it.exists() && it.isDirectory }
        ?: throw IOException("Your notebook folder is unavailable. Choose it again in Settings.")

    suspend fun list(tree: Uri): NotebookListing = withContext(Dispatchers.IO) {
        var unreadable = 0
        val entries = root(tree).listFiles().filter(DocumentFile::isDirectory).mapNotNull { folder ->
            val metadata = folder.findFile("entry.json") ?: return@mapNotNull null
            runCatching {
                val json = resolver.openInputStream(metadata.uri)?.bufferedReader()?.use { JSONObject(it.readText()) }
                    ?: throw IOException("Unable to read entry")
                require(json.getInt("version") == 1)
                val photo = folder.findFile(json.getString("photo")) ?: throw IOException("Missing solution")
                require(photo.length() > 0)
                val category = runCatching {
                    folder.findFile("site.txt")?.let { file ->
                        resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                    }
                }.getOrNull()
                NotebookEntry(json.getString("id"), json.getString("title"), json.getString("url"),
                    photo.uri, json.getLong("createdAt"),
                    site = ProblemSite.fromStored(category, json.getString("url")), directoryUri = folder.uri)
            }.getOrElse {
                unreadable++
                null
            }
        }.sortedByDescending(NotebookEntry::createdAt)
        NotebookListing(entries, unreadable)
    }

    suspend fun save(tree: Uri, title: String, url: String, sourcePhoto: Uri, site: ProblemSite): NotebookEntry =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val safeTitle = title.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), " ")
                .replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "Problem" }
            val directory = root(tree).createDirectory("$safeTitle — ${id.take(8)}")
                ?: throw IOException("Unable to create a problem folder. Check the folder's write access.")
            try {
                val mime = resolver.getType(sourcePhoto).orEmpty().ifBlank { "image/jpeg" }
                require(mime.startsWith("image/")) { "Choose a solution photo." }
                val extension = when (mime) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/heic", "image/heif" -> "heic"
                    else -> "jpg"
                }
                val photo = directory.createFile(mime, "solution.$extension")
                    ?: throw IOException("Unable to create the solution file.")
                val input = resolver.openInputStream(sourcePhoto) ?: throw IOException("The solution photo is unavailable.")
                val copied = input.use { source ->
                    val output = resolver.openOutputStream(photo.uri, "w") ?: throw IOException("Unable to write the solution photo.")
                    output.use { source.copyTo(it) }
                }
                require(copied > 0) { "The solution photo was empty. Take another photo." }
                val createdAt = System.currentTimeMillis()
                write(directory, "text/plain", "source.txt", "$title\n$url\n")
                write(directory, "text/plain", "site.txt", "${site.name}\n")
                // Written last. Only folders with complete metadata appear in the notebook.
                val metadata = JSONObject().put("version", 1).put("id", id).put("title", title)
                    .put("url", url).put("photo", photo.name).put("createdAt", createdAt)
                write(directory, "application/json", "entry.json", metadata.toString(2))
                NotebookEntry(id, title, url, photo.uri, createdAt, site, directory.uri)
            } catch (error: Exception) {
                directory.delete()
                throw error
            }
        }

    suspend fun changeSite(tree: Uri, entry: NotebookEntry, site: ProblemSite): NotebookEntry = withContext(Dispatchers.IO) {
        val directory = entryFolder(tree, entry)
        val file = directory.findFile("site.txt") ?: directory.createFile("text/plain", "site.txt")
            ?: throw IOException("Unable to create the site category file.")
        val output = resolver.openOutputStream(file.uri, "w") ?: throw IOException("Unable to save this category.")
        output.bufferedWriter().use { it.write("${site.name}\n") }
        entry.copy(site = site)
    }

    suspend fun delete(tree: Uri, entry: NotebookEntry) = withContext(Dispatchers.IO) {
        if (!entryFolder(tree, entry).delete()) {
            throw IOException("Unable to delete the solution folder. Check its write access and try again.")
        }
    }

    private fun entryFolder(tree: Uri, entry: NotebookEntry): DocumentFile {
        val folder = root(tree).listFiles().firstOrNull { it.isDirectory && it.uri == entry.directoryUri }
            ?: throw IOException("This solution folder is unavailable. Refresh your notebook.")
        val metadata = folder.findFile("entry.json") ?: throw IOException("This solution's metadata is missing.")
        val id = resolver.openInputStream(metadata.uri)?.bufferedReader()?.use { JSONObject(it.readText()).getString("id") }
        require(id == entry.id) { "The solution folder changed. Refresh your notebook before trying again." }
        return folder
    }

    private fun write(directory: DocumentFile, mime: String, name: String, contents: String) {
        val file = directory.createFile(mime, name) ?: throw IOException("Unable to create $name.")
        val stream = resolver.openOutputStream(file.uri, "w") ?: throw IOException("Unable to write $name.")
        stream.bufferedWriter().use { it.write(contents) }
    }
}
