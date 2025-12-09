package io.github.smiling_pixel.model

data class FileMetadata(
    val id: Long = 0,
    val originalFileName: String,
    val filePath: String,
    val tags: List<String>,
    val createdAt: Long
)
