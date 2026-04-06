package io.github.smiling_pixel.sync

import io.github.smiling_pixel.database.DiaryRepository
import kotlinx.coroutines.Job

/**
 * Starts platform-specific auto sync setup.
 *
 * @param repo Repository to synchronize.
 * @return The running auto-sync [Job], or `null` on platforms where auto-sync is unsupported.
 */
expect fun startAutoSync(repo: DiaryRepository): Job?
