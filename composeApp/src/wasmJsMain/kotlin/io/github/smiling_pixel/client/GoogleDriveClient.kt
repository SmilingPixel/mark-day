package io.github.smiling_pixel.client

/**
 * Implementation of [CloudDriveClient] for Google Drive on Web.
 * CURRENTLY NOT IMPLEMENTED.
 */
class GoogleDriveClient : CloudDriveClient {

    override suspend fun listFiles(parentId: String?): List<DriveFile> {
        throw NotImplementedError("Google Drive is not supported on Web target yet.")
    }

    override suspend fun createFile(name: String, content: ByteArray, mimeType: String, parentId: String?): DriveFile {
        throw NotImplementedError("Google Drive is not supported on Web target yet.")
    }

    override suspend fun createFolder(name: String, parentId: String?): DriveFile {
        throw NotImplementedError("Google Drive is not supported on Web target yet.")
    }

    override suspend fun deleteFile(fileId: String) {
        throw NotImplementedError("Google Drive is not supported on Web target yet.")
    }

    override suspend fun downloadFile(fileId: String): ByteArray {
        throw NotImplementedError("Google Drive is not supported on Web target yet.")
    }

    override suspend fun updateFile(fileId: String, content: ByteArray): DriveFile {
        throw NotImplementedError("Google Drive is not supported on Web target yet.")
    }

    override suspend fun isAuthorized(): Boolean {
        return false
    }

    override suspend fun authorize(): Boolean {
        throw NotImplementedError("Google Drive is not supported on Web target yet.")
    }

    override suspend fun signOut() {
        throw NotImplementedError("Google Drive is not supported on Web target yet.")
    }
    
    override suspend fun getUserInfo(): UserInfo? {
        return null
    }
}

actual fun getCloudDriveClient(): CloudDriveClient = GoogleDriveClient()
