package dev.breadcrumb.app

import android.content.ClipData
import android.content.ComponentName
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

object BrowserActions {
    const val IMAGE_SEARCH_PROMPT = "Give me the original link for this question. Return the coding problem's title and direct question URL."
    const val GITHUB_URL = "https://github.com/krish007code/Breadcrumb"

    fun copyImageSearchPrompt(context: Context) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Question link request", IMAGE_SEARCH_PROMPT))
    }
    fun open(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun searchText(context: Context, words: String) {
        val url = Uri.parse("https://www.google.com/search").buildUpon()
            .appendQueryParameter("q", words).build()
        open(context, url.toString())
    }

    fun searchImage(context: Context, photo: Uri) {
        copyImageSearchPrompt(context)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(photo) ?: "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, photo)
            putExtra(Intent.EXTRA_TEXT, IMAGE_SEARCH_PROMPT)
            putExtra(Intent.EXTRA_SUBJECT, "Find the original question link")
            clipData = ClipData.newRawUri("Question scan", photo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val google = Intent(share).setPackage("com.google.android.googlequicksearchbox")
        if (google.resolveActivity(context.packageManager) != null) {
            context.startActivity(google)
        } else {
            val chooser = Intent.createChooser(share, "Search this photo with Google or Lens")
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(context, MainActivity::class.java)))
            context.startActivity(chooser)
        }
    }
}
