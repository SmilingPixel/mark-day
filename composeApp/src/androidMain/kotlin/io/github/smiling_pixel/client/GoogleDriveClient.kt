package io.github.smiling_pixel.client

class GoogleDriveClient : CloudDriveClient {
    override suspend fun listFiles(parentId: String?): List<DriveFile> {
        TODO("Not yet implemented")
    }

    override suspend fun createFile(name: String, content: ByteArray, mimeType: String, parentId: String?): DriveFile {
        TODO("Not yet implemented")
    }

    override suspend fun createFolder(name: String, parentId: String?): DriveFile {
        TODO("Not yet implemented")
    }

    override suspend fun deleteFile(fileId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun downloadFile(fileId: String): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun updateFile(fileId: String, content: ByteArray): DriveFile {
        TODO("Not yet implemented")
    }

    override suspend fun isAuthorized(): Boolean {
        return false
    }

    override suspend fun authorize(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun signOut() {
        TODO("Not yet implemented")
    }

    override suspend fun getUserInfo(): UserInfo? {
        return null
    }
}

actual fun getCloudDriveClient(): CloudDriveClient = GoogleDriveClient()
