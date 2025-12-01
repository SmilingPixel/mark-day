package io.github.smiling_pixel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.smiling_pixel.database.createAndroidDiaryDao
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.preference.AndroidContextProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidContextProvider.context = this.applicationContext

        // Build Room-backed repository on Android and pass it into App
        val diaryDao = createAndroidDiaryDao(this)
        val repo = DiaryRepository(diaryDao)

        setContent {
            App(repo)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}