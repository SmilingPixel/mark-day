package io.github.smiling_pixel.sync

import io.github.smiling_pixel.database.DiaryRepository

actual fun startAutoSync(repo: DiaryRepository) {
    // Tell the user it's unsupported
    println("Auto sync is not supported on the Wasm platform.")
}
