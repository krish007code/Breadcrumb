package dev.breadcrumb.app

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

object BrowserActions {
    fun open(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun searchText(context: Context, words: String) {
        val url = Uri.parse("https://www.google.com/search").buildUpon()
            .appendQueryParameter("q", words).build()
        open(context, url.toString())
    }

    fun searchImage(context: Context, photo: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(photo) ?: "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, photo)
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
