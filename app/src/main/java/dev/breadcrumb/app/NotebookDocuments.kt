package dev.breadcrumb.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.util.UUID

/** Roll back provisional files if the operation exits before the commit point. */
internal inline fun <T> rollbackUnlessComplete(rollback: () -> Unit, operation: () -> T): T {
    var complete = false
    try {
        return operation().also { complete = true }
    } finally {
        if (!complete) rollback()
    }
}

internal class NotebookDocuments(context: Context) {
    private val resolver = context.contentResolver

    fun copyPhoto(directory: DocumentFile, source: Uri): DocumentFile {
        val mime = resolver.getType(source).orEmpty().ifBlank { "image/jpeg" }
        require(mime.startsWith("image/")) { "Choose a solution photo." }
        val extension =
            when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic",
                "image/heif" -> "heic"
                else -> "jpg"
            }
        val photo =
            directory.createFile(mime, "solution-${UUID.randomUUID()}.$extension")
                ?: throw IOException("Unable to create a solution photo.")
        return rollbackUnlessComplete({ photo.delete() }) {
            val copied = copyBytes(source, photo)
            require(copied > 0) { "A solution photo was empty. Choose another photo." }
            photo
        }
    }

    private fun copyBytes(source: Uri, photo: DocumentFile): Long {
        val input =
            resolver.openInputStream(source)
                ?: throw IOException("A selected photo is unavailable.")
        return input.use { data ->
            val output =
                resolver.openOutputStream(photo.uri, "w")
                    ?: throw IOException("Unable to write a solution photo.")
            output.use { data.copyTo(it) }
        }
    }

    fun read(file: DocumentFile): String =
        resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("Unable to read ${file.name}.")

    fun replaceText(directory: DocumentFile, name: String, contents: String) {
        val file =
            directory.findFile(name)
                ?: directory.createFile("text/plain", name)
                ?: throw IOException("Unable to create $name.")
        val output =
            resolver.openOutputStream(file.uri, "w") ?: throw IOException("Unable to write $name.")
        output.bufferedWriter().use { it.write(contents) }
    }

    fun write(directory: DocumentFile, mime: String, name: String, contents: String): DocumentFile {
        val file = directory.createFile(mime, name) ?: throw IOException("Unable to create $name.")
        return rollbackUnlessComplete({ file.delete() }) {
            val stream =
                resolver.openOutputStream(file.uri, "w")
                    ?: throw IOException("Unable to write $name.")
            stream.bufferedWriter().use { it.write(contents) }
            file
        }
    }
}
