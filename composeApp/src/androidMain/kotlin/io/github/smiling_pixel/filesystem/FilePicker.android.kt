package io.github.smiling_pixel.filesystem

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PlatformFile(
    val uri: Uri,
    private val contentResolver: ContentResolver
) {
    actual suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
    }

    actual fun name(): String {
        var name = ""
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: "unknown"
        }
        return name
    }
}

@Composable
actual fun rememberFilePicker(onFilesSelected: (List<PlatformFile>) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val files = uris.map { PlatformFile(it, contentResolver) }
        onFilesSelected(files)
    }

    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                launcher.launch(arrayOf("*/*"))
            }
        }
    }
}
