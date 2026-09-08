package io.github.smiling_pixel.filesystem

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.smiling_pixel.preference.AndroidContextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual suspend fun writeMomentExportFiles(files: List<MomentExportFile>): MomentExportResult =
    try {
        val context = AndroidContextProvider.context
        val uris =
            withContext(Dispatchers.IO) {
                val directory =
                    File(context.cacheDir, "moment_exports").also {
                        it.deleteRecursively()
                        it.mkdirs()
                    }
                ArrayList<Uri>(files.size).apply {
                    files.forEach { file ->
                        val exportFile = File(directory, file.fileName).apply { writeBytes(file.content) }
                        add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile))
                    }
                }
            }
        val intent =
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        intent.type = files.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(intent, "Share Moments").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        MomentExportResult.Success(files.size, "Android share sheet")
    } catch (e: Exception) {
        MomentExportResult.Failure("Moments could not be shared (${e::class.simpleName}).")
    }
