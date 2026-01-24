package io.github.smiling_pixel.client

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.util.Collections
import java.io.File as JavaFile

/**
 * Implementation of [CloudDriveClient] for Google Drive on JVM.
 * Uses the official Google Drive Java client library.
 *
 * References:
 * https://developers.google.com/workspace/drive/api/quickstart/java
 * https://developers.google.com/workspace/drive/api/guides/search-files
 * https://developers.google.com/workspace/drive/api/guides/manage-files
 *
 * IMPORTANT: This implementation requires a `credentials.json` file in the `src/jvmMain/resources` directory.
 * This file contains the OAuth 2.0 Client ID and Client Secret.
 * You can obtain this file from the Google Cloud Console:
 * 1. Go to APIs & Services > Credentials.
 * 2. Create an OAuth 2.0 Client ID for "Desktop app".
 * 3. Download the JSON file and rename it to `credentials.json`.
 * 4. Place it in `composeApp/src/jvmMain/resources/`.
 */
class GoogleDriveClient : CloudDriveClient {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val applicationName = "MarkDay Diary"
    
    /**
     * Directory to store authorization tokens for this application.
     */
    private val TOKENS_DIRECTORY_PATH = "tokens"

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private val SCOPES = listOf(DriveScopes.DRIVE_FILE)
    /**
     * Path to the credentials file in resources.
     * Ensure this file exists, otherwise [getFlow] will throw a [FileNotFoundException].
     */
    private val CREDENTIALS_FILE_PATH = "/credentials.json"

    private fun getFlow(httpTransport: NetHttpTransport): GoogleAuthorizationCodeFlow {
        val inputStream = GoogleDriveClient::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
            ?: throw FileNotFoundException(
                "Resource not found: $CREDENTIALS_FILE_PATH. " +
                "Please obtain credentials.json from Google Cloud Console and place it in src/jvmMain/resources."
            )
        
        val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream))

        return GoogleAuthorizationCodeFlow.Builder(
            httpTransport, jsonFactory, clientSecrets, SCOPES
        )
            .setDataStoreFactory(FileDataStoreFactory(JavaFile(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()
    }

    private fun getCredentials(httpTransport: NetHttpTransport): Credential {
        val flow = getFlow(httpTransport)
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        // authorize("user") authorizes for the "user" user ID.
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    private var _driveService: Drive? = null
    
    private fun getDriveService(): Drive {
        if (_driveService == null) {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val credential = getCredentials(httpTransport)
            _driveService = Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(applicationName)
                .build()
        }
        return _driveService!!
    }

    override suspend fun listFiles(parentId: String?): List<DriveFile> = withContext(Dispatchers.IO) {
        val folderId = parentId ?: "root"
        val query = "'$folderId' in parents and trashed = false"
        
        val result = getDriveService().files().list()
            .setQ(query)
            .setFields("nextPageToken, files(id, name, mimeType)")
            .execute()
            
        result.files?.map { file ->
            DriveFile(
                id = file.id,
                name = file.name,
                mimeType = file.mimeType,
                isFolder = file.mimeType == MIME_TYPE_FOLDER
            )
        } ?: emptyList()
    }

    override suspend fun createFile(name: String, content: ByteArray, mimeType: String, parentId: String?): DriveFile = withContext(Dispatchers.IO) {
        val fileMetadata = File().apply {
            this.name = name
            this.mimeType = mimeType
            if (parentId != null) {
                this.parents = listOf(parentId)
            }
        }
        
        val mediaContent = ByteArrayContent(mimeType, content)
        
        val file = getDriveService().files().create(fileMetadata, mediaContent)
            .setFields("id, name, mimeType, parents")
            .execute()
            
        DriveFile(
            id = file.id,
            name = file.name,
            mimeType = file.mimeType,
            isFolder = file.mimeType == MIME_TYPE_FOLDER
        )
    }

    override suspend fun createFolder(name: String, parentId: String?): DriveFile = withContext(Dispatchers.IO) {
        val fileMetadata = File().apply {
            this.name = name
            this.mimeType = MIME_TYPE_FOLDER
            if (parentId != null) {
                this.parents = listOf(parentId)
            }
        }
        
        val file = getDriveService().files().create(fileMetadata)
            .setFields("id, name, mimeType")
            .execute()
            
        DriveFile(
            id = file.id,
            name = file.name,
            mimeType = MIME_TYPE_FOLDER,
            isFolder = true
        )
    }

    override suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        getDriveService().files().delete(fileId).execute()
    }

    override suspend fun downloadFile(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        getDriveService().files().get(fileId)
            .executeMediaAndDownloadTo(outputStream)
        outputStream.toByteArray()
    }

    override suspend fun updateFile(fileId: String, content: ByteArray): DriveFile = withContext(Dispatchers.IO) {
        // Retrieve current metadata to keep name/mimeType if needed, or just update content
        // Creating a new File object with empty metadata to only update content is possible,
        // but often we might want to update modified time etc.
        val fileMetadata = File() 
        
        // We need to guess the mime type or retrieve it. For update, let's assume we keep existing or use generic.
        // But ByteArrayContent needs a type. 
        // Let's fetch the file first to get the mimeType.
        val existingFile = getDriveService().files().get(fileId).setFields("mimeType").execute()
        val mimeType = existingFile.mimeType
        
        val mediaContent = ByteArrayContent(mimeType, content)
        
        val updatedFile = getDriveService().files().update(fileId, fileMetadata, mediaContent)
            .setFields("id, name, mimeType")
            .execute()
            
        DriveFile(
            id = updatedFile.id,
            name = updatedFile.name,
            mimeType = updatedFile.mimeType,
            isFolder = updatedFile.mimeType == MIME_TYPE_FOLDER
        )
    }

    override suspend fun isAuthorized(): Boolean = withContext(Dispatchers.IO) {
        try {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val flow = getFlow(httpTransport)
            val credential = flow.loadCredential("user")
            
            if (credential == null) return@withContext false
            
            val refreshToken = credential.refreshToken
            val expiresIn = credential.expiresInSeconds
            // Authorized if we have a refresh token OR a valid access token
            return@withContext refreshToken != null || (expiresIn != null && expiresIn > 60)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun authorize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Force re-authorization or load existing
            // accessing driveService triggers authorization via getCredentials
            // But getCredentials calls `authorize("user")`
            // If we are already authorized, this returns immediately.
            // If not, it opens browser.
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val credential = getCredentials(httpTransport)
            credential != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val flow = getFlow(httpTransport)
        flow.credentialDataStore.delete("user")
        _driveService = null
    }

    override suspend fun getUserInfo(): UserInfo? = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext null
        try {
            val about = getDriveService().about().get().setFields("user").execute()
            val user = about.user
            UserInfo(
                name = user.displayName,
                email = user.emailAddress,
                photoUrl = user.photoLink
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    }
}

private val googleDriveClientInstance by lazy { GoogleDriveClient() }
actual fun getCloudDriveClient(): CloudDriveClient = googleDriveClientInstance
