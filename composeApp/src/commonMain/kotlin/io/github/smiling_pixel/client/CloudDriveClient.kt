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
    companion object {
        const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    }

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

    /**
     * Checks whether the client is currently authorized to access the cloud drive.
     * This method should not trigger any user interaction.
     *
     * @return `true` if the client has a valid authorization/session, `false` otherwise.
     * @throws Exception If the authorization state cannot be determined due to an underlying error.
     */
    suspend fun isAuthorized(): Boolean

    /**
     * Initiates the authorization or sign-in flow required to access the cloud drive.
     *
     * @return `true` if authorization completes successfully and the client is ready to use,
     *         `false` if the user cancels or authorization otherwise fails without throwing.
     * @throws Exception If an unrecoverable error occurs during authorization (for example,
     *         network failures or provider-specific errors).
     */
    suspend fun authorize(): Boolean

    /**
     * Signs out the current user and revokes or clears any stored authorization.
     *
     * @throws Exception If an error occurs while signing out or revoking authorization.
     */
    suspend fun signOut()

    /**
     * Retrieves basic information about the currently authorized user.
     *
     * @return A [UserInfo] object for the current user, or `null` if there is no authorized user.
     * @throws Exception If user information cannot be retrieved due to authorization or
     *         connectivity issues.
     */
    suspend fun getUserInfo(): UserInfo?
}

expect fun getCloudDriveClient(): CloudDriveClient

