package io.github.smiling_pixel.filesystem

class InMemoryFileManager : FileManager {
    private val files = mutableMapOf<String, ByteArray>()

    override suspend fun save(
        fileName: String,
        content: ByteArray,
    ) {
        files[fileName] = content
    }

    override suspend fun read(fileName: String): ByteArray? = files[fileName]

    override suspend fun delete(fileName: String) {
        files.remove(fileName)
    }

    override suspend fun exists(fileName: String): Boolean = files.containsKey(fileName)

    override suspend fun list(): List<String> = files.keys.toList()

    override suspend fun getSize(fileName: String): Long = files[fileName]?.size?.toLong() ?: 0L
}
