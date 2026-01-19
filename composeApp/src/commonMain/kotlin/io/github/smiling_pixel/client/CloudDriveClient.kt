package io.github.smiling_pixel.client

/**
 * Represents a file or folder in the cloud drive.
 */
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val isFolder: Boolean
)

data class UserInfo(
    val name: String,
    val email: String,
    val photoUrl: String? = null
)

/**
 * Client interface for accessing and managing files on cloud drives.
 */
interface CloudDriveClient {
    /**
     * Lists files and folders in the specified parent folder.
     * @param parentId The ID of the parent folder. If null, lists files in the root.
     * @return List of [DriveFile]s.
     */
    suspend fun listFiles(parentId: String? = null): List<DriveFile>

    /**
     * Creates a new file.
     * @param name The name of the file.
     * @param content The content of the file.
     * @param mimeType The MIME type of the file.
     * @param parentId The ID of the parent folder. If null, creates in the root.
     * @return The created [DriveFile].
     */
    suspend fun createFile(name: String, content: ByteArray, mimeType: String, parentId: String? = null): DriveFile

    /**
     * Creates a new folder.
     * @param name The name of the folder.
     * @param parentId The ID of the parent folder. If null, creates in the root.
     * @return The created folder as a [DriveFile].
     */
    suspend fun createFolder(name: String, parentId: String? = null): DriveFile

    /**
     * Deletes a file or folder.
     * @param fileId The ID of the file or folder to delete.
     */
    suspend fun deleteFile(fileId: String)

    /**
     * Downloads the content of a file.
     * @param fileId The ID of the file to download.
     * @return The content of the file as [ByteArray].
     */
    suspend fun downloadFile(fileId: String): ByteArray

    /**
     * Updates the content of an existing file.
     * @param fileId The ID of the file to update.
     * @param content The new content of the file.
     * @return The updated [DriveFile].
     */
    suspend fun updateFile(fileId: String, content: ByteArray): DriveFile

    suspend fun isAuthorized(): Boolean
    suspend fun authorize(): Boolean
    suspend fun signOut()
    suspend fun getUserInfo(): UserInfo?
}

expect fun getCloudDriveClient(): CloudDriveClient

