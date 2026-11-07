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
                NotebookEntry(json.getString("id"), json.getString("title"), json.getString("url"),
                    photo.uri, json.getLong("createdAt"))
            }.getOrElse {
                unreadable++
                null
            }
        }.sortedByDescending(NotebookEntry::createdAt)
        NotebookListing(entries, unreadable)
    }

    suspend fun save(tree: Uri, title: String, url: String, sourcePhoto: Uri): NotebookEntry =
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
                // Written last. Only folders with complete metadata appear in the notebook.
                val metadata = JSONObject().put("version", 1).put("id", id).put("title", title)
                    .put("url", url).put("photo", photo.name).put("createdAt", createdAt)
                write(directory, "application/json", "entry.json", metadata.toString(2))
                NotebookEntry(id, title, url, photo.uri, createdAt)
            } catch (error: Exception) {
                directory.delete()
                throw error
            }
        }

    private fun write(directory: DocumentFile, mime: String, name: String, contents: String) {
        val file = directory.createFile(mime, name) ?: throw IOException("Unable to create $name.")
        val stream = resolver.openOutputStream(file.uri, "w") ?: throw IOException("Unable to write $name.")
        stream.bufferedWriter().use { it.write(contents) }
    }
}
