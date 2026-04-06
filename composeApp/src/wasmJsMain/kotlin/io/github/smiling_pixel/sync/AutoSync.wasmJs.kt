package io.github.smiling_pixel.sync

import io.github.smiling_pixel.database.DiaryRepository
import kotlinx.coroutines.Job

actual fun startAutoSync(repo: DiaryRepository): Job? {
    // Tell the user it's unsupported
    println("Auto sync is not supported on the Wasm platform.")
    return null
}
