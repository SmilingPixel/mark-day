package io.github.smiling_pixel.client

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object GoogleSignInHelper {
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var authDeferred: CompletableDeferred<ActivityResult>? = null
    
    // Mutex to handle concurrent authorization requests.
    // This prevents race conditions where a second sign-in request could overwrite
    // the 'authDeferred' of a pending request, causing the first request to never complete.
    private val mutex = Mutex()

    fun registerLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.launcher = launcher
    }

    fun onActivityResult(result: ActivityResult) {
        authDeferred?.complete(result)
        authDeferred = null
    }

    suspend fun launchSignIn(intent: Intent): ActivityResult? {
        val l = launcher ?: return null
        
        // Use a Mutex to ensure only one sign-in flow is active at a time.
        // We wait for the lock, then create the deferred, launch the intent, and wait for the result.
        // The lock is held until the result is received (or the coroutine is cancelled),
        // preventing other coroutines from overwriting 'authDeferred' in the meantime.
        return mutex.withLock {
            val deferred = CompletableDeferred<ActivityResult>()
            authDeferred = deferred
            l.launch(intent)
            deferred.await()
        }
    }
}
