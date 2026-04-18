package io.github.smiling_pixel.sync

internal const val AUTO_SYNC_INTERVAL_MS = 15 * 60 * 1000L
internal const val AUTO_SYNC_MAX_BACKOFF_MS = 2 * 60 * 60 * 1000L
internal const val AUTO_SYNC_LOG_TAG = "AutoSync"
internal const val AUTO_SYNC_ERROR_MESSAGE = "Auto-sync failed; retry interval will back off."