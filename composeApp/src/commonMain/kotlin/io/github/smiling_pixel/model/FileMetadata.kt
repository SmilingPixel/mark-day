package io.github.smiling_pixel.model

/**
 * Metadata for a file owned by the local Moments library.
 *
 * @property id Local database identifier.
 * @property originalFileName User-visible name supplied by the source file.
 * @property filePath Opaque, repository-owned storage key.
 * @property tags User-defined labels retained for forward compatibility.
 * @property createdAt Epoch milliseconds at which the file was added to Moments.
 * @property mimeType Best-effort media type used for preview and export behavior.
 * @property sizeBytes Persisted file size, or `-1` until legacy metadata is backfilled.
 */
data class FileMetadata(
    val id: Long = 0,
    val originalFileName: String,
    val filePath: String,
    val tags: List<String>,
    val createdAt: Long,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = -1,
)
