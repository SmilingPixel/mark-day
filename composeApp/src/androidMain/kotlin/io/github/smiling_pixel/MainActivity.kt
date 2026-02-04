package io.github.smiling_pixel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.smiling_pixel.database.createDatabase
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.fileManager
import io.github.smiling_pixel.preference.AndroidContextProvider

import androidx.activity.result.contract.ActivityResultContracts
import io.github.smiling_pixel.client.GoogleSignInHelper

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        GoogleSignInHelper.registerLauncher(
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                GoogleSignInHelper.onActivityResult(result)
            }
        )
        AndroidContextProvider.context = this.applicationContext

        // Build Room-backed repository on Android and pass it into App
        val db = createDatabase(this)
        val repo = DiaryRepository(db.diaryDao())
        val fileRepo = FileRepository(fileManager, db.fileMetadataDao())

        setContent {
            App(repo, fileRepo)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GoogleSignInHelper.unregisterLauncher()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}