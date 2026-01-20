package io.github.smiling_pixel.client

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.CompletableDeferred

object GoogleSignInHelper {
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var authDeferred: CompletableDeferred<ActivityResult>? = null

    fun registerLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.launcher = launcher
    }

    fun onActivityResult(result: ActivityResult) {
        authDeferred?.complete(result)
        authDeferred = null
    }

    suspend fun launchSignIn(intent: Intent): ActivityResult? {
        val l = launcher ?: return null
        val deferred = CompletableDeferred<ActivityResult>()
        authDeferred = deferred
        l.launch(intent)
        return deferred.await()
    }
}
