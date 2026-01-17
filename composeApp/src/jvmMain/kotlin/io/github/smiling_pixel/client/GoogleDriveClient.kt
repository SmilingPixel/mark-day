package io.github.smiling_pixel.client

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Implementation of [CloudDriveClient] for Google Drive on JVM.
 * Uses the official Google Drive Java client library.
 *
 * References:
 * https://developers.google.com/workspace/drive/api/quickstart/java
 * https://developers.google.com/workspace/drive/api/guides/search-files
 * https://developers.google.com/workspace/drive/api/guides/manage-files
 */
class GoogleDriveClient : CloudDriveClient {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val applicationName = "MarkDay Diary"

    // TODO: Initialize Drive service with proper authentication.
    // The authentication process requires a valid Credential object.
    // See: https://developers.google.com/workspace/drive/api/modules/auth
    private val driveService: Drive by lazy {
        // TODO: Handle GeneralSecurityException and IOException properly in a real app or during init
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        
        // TODO: Retrieve the actual credential (e.g., from an authorized user session).
        // This is a placeholder. You need to implement the OAuth2 flow or pass a valid Credential.
        /*
        val credential = GoogleCredential.Builder()
            .setTransport(httpTransport)
            .setJsonFactory(jsonFactory)
            .setClientSecrets(...)
            .build()
        */
        val credential = null // Placeholder for compilation (will throw NPE at runtime if used)

        Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(applicationName)
            .build()
    }

    override suspend fun listFiles(parentId: String?): List<DriveFile> = withContext(Dispatchers.IO) {
        val folderId = parentId ?: "root"
        val query = "'$folderId' in parents and trashed = false"
        
        val result = driveService.files().list()
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
        
        val file = driveService.files().create(fileMetadata, mediaContent)
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
        
        val file = driveService.files().create(fileMetadata)
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
        driveService.files().delete(fileId).execute()
    }

    override suspend fun downloadFile(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        driveService.files().get(fileId)
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
        val existingFile = driveService.files().get(fileId).setFields("mimeType").execute()
        val mimeType = existingFile.mimeType
        
        val mediaContent = ByteArrayContent(mimeType, content)
        
        val updatedFile = driveService.files().update(fileId, fileMetadata, mediaContent)
            .setFields("id, name, mimeType")
            .execute()
            
        DriveFile(
            id = updatedFile.id,
            name = updatedFile.name,
            mimeType = updatedFile.mimeType,
            isFolder = updatedFile.mimeType == MIME_TYPE_FOLDER
        )
    }

    companion object {
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    }
}
