package io.github.smiling_pixel.filesystem

import okio.FileSystem
import okio.Path.Companion.toPath

interface FileManager {
    suspend fun save(fileName: String, content: ByteArray)
    suspend fun read(fileName: String): ByteArray?
    suspend fun delete(fileName: String)
    suspend fun exists(fileName: String): Boolean
    suspend fun list(): List<String>
}

class LocalFileManager(
    private val fileSystem: FileSystem,
    private val rootDir: String
) : FileManager {

    private val rootPath = rootDir.toPath()

    init {
        if (!fileSystem.exists(rootPath)) {
            fileSystem.createDirectories(rootPath)
        }
    }

    override suspend fun save(fileName: String, content: ByteArray) {
        val filePath = rootPath / fileName
        // Ensure parent directories exist
        val parent = filePath.parent
        if (parent != null && !fileSystem.exists(parent)) {
            fileSystem.createDirectories(parent)
        }
        fileSystem.write(filePath) {
            write(content)
        }
    }

    override suspend fun read(fileName: String): ByteArray? {
        val filePath = rootPath / fileName
        return if (fileSystem.exists(filePath)) {
            fileSystem.read(filePath) {
                readByteArray()
            }
        } else {
            null
        }
    }

    override suspend fun delete(fileName: String) {
        val filePath = rootPath / fileName
        if (fileSystem.exists(filePath)) {
            fileSystem.delete(filePath)
        }
    }

    override suspend fun exists(fileName: String): Boolean {
        return fileSystem.exists(rootPath / fileName)
    }

    override suspend fun list(): List<String> {
        return if (fileSystem.exists(rootPath)) {
            fileSystem.list(rootPath).map { it.name }
        } else {
            emptyList()
        }
    }
}


expect val fileManager: FileManager
