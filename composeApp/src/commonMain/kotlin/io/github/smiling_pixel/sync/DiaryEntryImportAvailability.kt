package io.github.smiling_pixel.sync

/**
 * Returns whether diary entry import is available on the current platform.
 *
 * @return `true` when local diary entry files can be selected and imported.
 */
expect fun isDiaryEntryImportAvailable(): Boolean
