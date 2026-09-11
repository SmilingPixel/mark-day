package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.util.Logger
import io.github.smiling_pixel.util.i

/** Result of exporting one or more Moments through a platform-native destination. */
sealed interface MomentExportResult {
    /** Export completed for every requested Moment. */
    data class Success(
        val exportedCount: Int,
        val destinationDescription: String,
    ) : MomentExportResult

    /** Some Moments were exported while others were unavailable. */
    data class PartialSuccess(
        val exportedCount: Int,
        val failedCount: Int,
        val destinationDescription: String,
    ) : MomentExportResult

    /** The user dismissed the platform destination UI. */
    data object Cancelled : MomentExportResult

    /** Export is not available on the current platform. */
    data object Unavailable : MomentExportResult

    /** No selected Moment could be exported. */
    data class Failure(
        val message: String,
    ) : MomentExportResult
}

/** Exports available [files] while reporting missing bytes as partial failure. */
suspend fun FileRepository.exportMoments(files: List<FileMetadata>): MomentExportResult {
    val exportFiles = mutableListOf<MomentExportFile>()
    var missing = 0
    files.forEach { metadata ->
        val bytes = runCatching { getFileContent(metadata) }.getOrNull()
        if (bytes == null) {
            missing++
        } else {
            exportFiles += MomentExportFile(sanitizeExportName(metadata.originalFileName), metadata.mimeType, bytes)
        }
    }
    if (exportFiles.isEmpty()) {
        Logger.w("MomentsExport", "no_available_files requested=${files.size}")
        return MomentExportResult.Failure("The selected files are unavailable.")
    }
    val platformResult = writeMomentExportFiles(uniqueExportNames(exportFiles))
    val result =
        if (missing > 0 && platformResult is MomentExportResult.Success) {
            MomentExportResult.PartialSuccess(
                platformResult.exportedCount,
                missing,
                platformResult.destinationDescription,
            )
        } else {
            platformResult
        }
    Logger.i("MomentsExport", "complete requested=${files.size} available=${exportFiles.size} missing=$missing")
    return result
}

internal data class MomentExportFile(
    val fileName: String,
    val mimeType: String,
    val content: ByteArray,
)

internal expect suspend fun writeMomentExportFiles(files: List<MomentExportFile>): MomentExportResult

internal fun sanitizeExportName(fileName: String): String {
    val cleaned = fileName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
    return cleaned.take(180).ifBlank { "moment" }
}

private fun uniqueExportNames(files: List<MomentExportFile>): List<MomentExportFile> {
    val used = mutableSetOf<String>()
    return files.map { file ->
        var candidate = file.fileName
        var suffix = 2
        while (!used.add(candidate.lowercase())) {
            val dot = file.fileName.lastIndexOf('.')
            val base = if (dot > 0) file.fileName.substring(0, dot) else file.fileName
            val extension = if (dot > 0) file.fileName.substring(dot) else ""
            candidate = "$base ($suffix)$extension"
            suffix++
        }
        file.copy(fileName = candidate)
    }
}
