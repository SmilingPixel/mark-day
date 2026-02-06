package io.github.smiling_pixel.client

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import io.github.smiling_pixel.util.Logger
import io.github.smiling_pixel.util.e
import io.github.smiling_pixel.preference.AndroidContextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.Collections

class GoogleDriveClient : CloudDriveClient {

    /**
     * Retrieves the Android Application Context.
     *
     * This property accesses [AndroidContextProvider.context]. Ensure that [AndroidContextProvider.context]
     * is initialized (typically in `MainActivity.onCreate`) before any methods of this client are called.
     *
     * @throws IllegalStateException if [AndroidContextProvider.context] has not been initialized yet.
     */
    private val context: Context
        get() = try {
            AndroidContextProvider.context
        } catch (e: UninitializedPropertyAccessException) {
            throw IllegalStateException(
                "AndroidContextProvider.context is not initialized. " +
                    "Ensure it is set before using GoogleDriveClient.",
                e
            )
        }

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val appName = "MarkDay Diary"

    private val serviceMutex = Mutex()
    @Volatile
    private var driveService: Drive? = null

    private fun getService(): Drive {
        return driveService ?: throw IllegalStateException("Google Drive not authorized")
    }

    // Checking auth state and initializing service if possible
    private suspend fun checkAndInitService(): Boolean {
        if (driveService != null) return true
        
        return serviceMutex.withLock {
            if (driveService != null) return@withLock true

            val account = GoogleSignIn.getLastSignedInAccount(context)
            val driveScope = Scope(DriveScopes.DRIVE_FILE)

            if (account != null && GoogleSignIn.hasPermissions(account, driveScope)) {
                val email = account.email
                if (email != null) {
                    initService(email)
                    return@withLock true
                }
            }
            false
        }
    }

    private fun initService(email: String) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccountName = email
        
        driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            jsonFactory,
            credential
        ).setApplicationName(appName).build()
    }

    override suspend fun isAuthorized(): Boolean = withContext(Dispatchers.IO) {
        checkAndInitService()
    }

    override suspend fun authorize(): Boolean {
        // First, try to initialize the service on IO without switching to Main unnecessarily
        if (withContext(Dispatchers.IO) { checkAndInitService() }) return true
        // If not yet authorized, perform the sign-in flow on the main thread
        return withContext(Dispatchers.Main) {
            val driveScope = Scope(DriveScopes.DRIVE_FILE)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(driveScope)
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            val signInIntent = client.signInIntent
            val result = GoogleSignInHelper.launchSignIn(signInIntent)
            if (result != null && result.resultCode == android.app.Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    if (account != null) {
                        val email = account.email
                        if (email != null) {
                            serviceMutex.withLock {
                                initService(email)
                            }
                            return@withContext true
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("GoogleDriveClient", "Authorization failed: ${e.message}")
                    e.printStackTrace()
                }
            }
            false
        }
    }

    override suspend fun signOut() = withContext(Dispatchers.Main) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val client = GoogleSignIn.getClient(context, gso)
        
        val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
        client.signOut().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                driveService = null
                deferred.complete(Unit)
            } else {
                val e = task.exception ?: RuntimeException("Sign out failed")
                Logger.e("GoogleDriveClient", "Sign out failed: ${e.message}")
                deferred.completeExceptionally(e)
            }
        }
        deferred.await()
    }

    override suspend fun getUserInfo(): UserInfo? = withContext(Dispatchers.IO) {
        if (!checkAndInitService()) return@withContext null
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
        val photoUrl = account.photoUrl?.toString()
        UserInfo(
            name = account.displayName ?: "",
            email = account.email ?: "",
            photoUrl = photoUrl
        )
    }

    override suspend fun listFiles(parentId: String?): List<DriveFile> = withContext(Dispatchers.IO) {
        val folderId = parentId ?: "root"
        // Sanitize folderId to prevent injection
        val sanitizedFolderId = folderId.replace("'", "\\'")
        val query = "'$sanitizedFolderId' in parents and trashed = false"
        
        val result = getService().files().list()
            .setQ(query)
            .setFields("nextPageToken, files(id, name, mimeType)")
            .execute()
            
        result.files?.map { file ->
            DriveFile(
                id = file.id,
                name = file.name,
                mimeType = file.mimeType,
                isFolder = file.mimeType == CloudDriveClient.MIME_TYPE_FOLDER
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
        
        val file = getService().files().create(fileMetadata, mediaContent)
            .setFields("id, name, mimeType, parents")
            .execute()
            
        DriveFile(
            id = file.id,
            name = file.name,
            mimeType = file.mimeType,
            isFolder = file.mimeType == CloudDriveClient.MIME_TYPE_FOLDER
        )
    }

    override suspend fun createFolder(name: String, parentId: String?): DriveFile = withContext(Dispatchers.IO) {
        val fileMetadata = File().apply {
            this.name = name
            this.mimeType = CloudDriveClient.MIME_TYPE_FOLDER
            if (parentId != null) {
                this.parents = listOf(parentId)
            }
        }
        
        val file = getService().files().create(fileMetadata)
            .setFields("id, name, mimeType")
            .execute()
            
        DriveFile(
            id = file.id,
            name = file.name,
            mimeType = CloudDriveClient.MIME_TYPE_FOLDER,
            isFolder = true
        )
    }

    override suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        getService().files().delete(fileId).execute()
    }

    override suspend fun downloadFile(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        getService().files().get(fileId)
            .executeMediaAndDownloadTo(outputStream)
        outputStream.toByteArray()
    }

    override suspend fun updateFile(fileId: String, content: ByteArray): DriveFile = withContext(Dispatchers.IO) {
        val fileMetadata = File() 
        val existingFile = getService().files().get(fileId).setFields("mimeType").execute()
        val mimeType = existingFile.mimeType
        
        val mediaContent = ByteArrayContent(mimeType, content)
        
        val updatedFile = getService().files().update(fileId, fileMetadata, mediaContent)
            .setFields("id, name, mimeType")
            .execute()
            
        DriveFile(
            id = updatedFile.id,
            name = updatedFile.name,
            mimeType = updatedFile.mimeType,
            isFolder = updatedFile.mimeType == CloudDriveClient.MIME_TYPE_FOLDER
        )
    }
}

private val googleDriveClientInstance by lazy { GoogleDriveClient() }
actual fun getCloudDriveClient(): CloudDriveClient = googleDriveClientInstance