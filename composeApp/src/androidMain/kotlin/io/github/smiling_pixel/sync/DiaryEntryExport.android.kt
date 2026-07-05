package io.github.smiling_pixel.sync

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.smiling_pixel.preference.AndroidContextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual suspend fun writeDiaryEntryExportFiles(
    files: List<DiaryEntryExportFile>
): DiaryEntryExportResult {
    return try {
        val context = AndroidContextProvider.context

        val uris: ArrayList<Uri> = withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "entry_exports").also {
                if (it.exists()) {
                    it.deleteRecursively()
                }
                it.mkdirs()
            }
            ArrayList<Uri>(files.size).apply {
                for (file in files) {
                    val exportFile = File(exportDir, file.fileName)
                    exportFile.writeBytes(file.content)
                    add(
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            exportFile
                        )
                    )
                }
            }
        }

        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "MarkDay diary entries")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Export MarkDay diary entries").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)

        DiaryEntryExportResult.Success(files.size, "Android share sheet")
    } catch (e: Exception) {
        DiaryEntryExportResult.Failure(e.message ?: "Unable to export diary entries.")
    }
}
