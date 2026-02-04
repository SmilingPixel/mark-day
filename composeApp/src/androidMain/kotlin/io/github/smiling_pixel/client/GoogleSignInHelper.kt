package io.github.smiling_pixel.client

import android.content.Intent
import android.util.Log
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

    fun unregisterLauncher() {
        this.launcher = null
    }

    fun onActivityResult(result: ActivityResult) {
        if (authDeferred == null) {
            Log.w("GoogleSignInHelper", "onActivityResult called but authDeferred is null. Unexpected activity result or cancelled sign-in.")
        }
        authDeferred?.complete(result)
        authDeferred = null
    }

    suspend fun launchSignIn(intent: Intent): ActivityResult? {

        // Use a Mutex to ensure only one sign-in flow is active at a time.
        // We wait for the lock, then create the deferred, launch the intent, and wait for the result.
        // The lock is held until the result is received (or the coroutine is cancelled),
        // preventing other coroutines from overwriting 'authDeferred' in the meantime.
        
        val l = launcher ?: return null

        val deferred = CompletableDeferred<ActivityResult>()

        // Update authDeferred safely. If a previous request is pending, cancel it
        // so we don't block indefinitely (e.g. if the user abandoned the previous sign-in).
        mutex.withLock {
            authDeferred?.cancel()
            authDeferred = deferred
            l.launch(intent)
        }

        try {
            return deferred.await()
        } finally {
            // Ensure proper cleanup. Only clear if the current deferred is the one we set.
            mutex.withLock {
                if (authDeferred === deferred) {
                    authDeferred = null
                }
            }
        }
    }
}
