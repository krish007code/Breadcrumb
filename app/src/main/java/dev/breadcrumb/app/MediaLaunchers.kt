package dev.breadcrumb.app

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class MediaLaunchers(
    val folder: () -> Unit,
    val capture: (String) -> Unit,
    val pickScan: () -> Unit,
    val pickSolutions: () -> Unit,
)

@Composable
internal fun rememberMediaLaunchers(
    choose: (android.net.Uri) -> Unit,
    report: (String) -> Unit,
    prepareCamera: (String) -> android.net.Uri,
    cameraResult: (Boolean) -> Unit,
    importPhotos: (String, List<android.net.Uri>) -> Unit,
): MediaLaunchers {
    val context = LocalContext.current
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                        choose(uri)
                    }
                    .onFailure {
                        report("Unable to use this folder. Choose another folder you can write to.")
                    }
            }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), cameraResult)
    val scanPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { importPhotos("scan", listOf(it)) }
        }
    val solutionPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris
            ->
            importPhotos("solution", uris)
        }
    fun capture(kind: String) {
        runCatching { camera.launch(prepareCamera(kind)) }
            .onFailure {
                cameraResult(false)
                report("No camera app is available. Import a photo instead.")
            }
    }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    return MediaLaunchers(
        { folderPicker.launch(null) },
        ::capture,
        { scanPicker.launch(request) },
        { solutionPicker.launch(request) },
    )
}

@Composable
internal fun rememberScanExport(
    scan: () -> android.net.Uri?,
    report: (String) -> Unit,
): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportScan =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) {
            destination ->
            val source = scan()
            if (destination != null && source != null) {
                scope.launch {
                    runCatching {
                            withContext(Dispatchers.IO) {
                                val input =
                                    context.contentResolver.openInputStream(source)
                                        ?: error("Scan unavailable")
                                input.use { data ->
                                    val output =
                                        context.contentResolver.openOutputStream(destination, "w")
                                            ?: error("File unavailable")
                                    output.use { data.copyTo(it) }
                                }
                            }
                            BrowserActions.copyImageSearchPrompt(context)
                            BrowserActions.open(context, "https://lens.google/")
                            report(
                                "Select your saved photo in Lens. The request for its question link " +
                                    "is copied for Ask about this image."
                            )
                        }
                        .onFailure {
                            report(
                                "Couldn't open Lens. Your scan can still be selected from the location you saved it."
                            )
                        }
                }
            }
        }

    return { exportScan.launch(it) }
}
