package io.github.smiling_pixel.sync

import io.github.smiling_pixel.database.DiaryRepository

/**
 * Platform-specific auto sync setup.
 */
expect fun startAutoSync(repo: DiaryRepository)
