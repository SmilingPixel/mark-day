package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.util.Logger
import io.github.smiling_pixel.util.i

/** Picker metadata used to enforce limits before importing bytes. */
data class MomentImportCandidate(
    val platformFile: PlatformFile,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String,
)

/** Result of inspecting a picker selection. */
data class MomentImportPreflight(
    val accepted: List<MomentImportCandidate>,
    val rejectedCount: Int,
    val requiresLargeFileConfirmation: Boolean,
)

/** Aggregate result of a sequential Moment import. */
data class MomentImportResult(
    val imported: List<FileMetadata>,
    val rejectedCount: Int,
    val failedCount: Int,
)

/** Inspects [files], rejecting known files above the hard size limit. */
fun inspectMomentImports(files: List<PlatformFile>): MomentImportPreflight {
    val candidates =
        files.map { file ->
            val name = file.name().ifBlank { "Untitled file" }
            MomentImportCandidate(
                platformFile = file,
                displayName = name,
                sizeBytes = file.sizeBytes(),
                mimeType = file.mimeType()?.takeIf(String::isNotBlank) ?: inferMimeType(name),
            )
        }
    val accepted = candidates.filter { it.sizeBytes == null || it.sizeBytes <= MAX_MOMENT_BYTES }
    val rejectedCount = candidates.size - accepted.size
    if (rejectedCount > 0) {
        Logger.w("MomentsImport", "size_limit_rejected count=$rejectedCount")
    }
    return MomentImportPreflight(
        accepted = accepted,
        rejectedCount = rejectedCount,
        requiresLargeFileConfirmation = accepted.any { (it.sizeBytes ?: 0) > LARGE_MOMENT_WARNING_BYTES },
    )
}

/** Imports [preflight] sequentially and continues after per-file failures. */
suspend fun FileRepository.importMoments(preflight: MomentImportPreflight): MomentImportResult {
    val imported = mutableListOf<FileMetadata>()
    var failedCount = 0
    for (candidate in preflight.accepted) {
        try {
            val bytes = candidate.platformFile.readBytes(MAX_MOMENT_BYTES)
            imported += saveFile(candidate.displayName, bytes, mimeType = candidate.mimeType)
        } catch (e: Exception) {
            failedCount++
            Logger.e("MomentsImport", "file_failed type=${e::class.simpleName}")
        }
    }
    Logger.i(
        "MomentsImport",
        "complete attempted=${preflight.accepted.size + preflight.rejectedCount} " +
            "imported=${imported.size} rejected=${preflight.rejectedCount} failed=$failedCount",
    )
    return MomentImportResult(imported, preflight.rejectedCount, failedCount)
}
