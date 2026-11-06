package io.github.smiling_pixel.draft

import io.github.smiling_pixel.preference.AndroidContextProvider

internal actual fun produceDraftPath(): String =
    AndroidContextProvider.context.filesDir
        .resolve(DRAFT_DATA_STORE_FILE_NAME)
        .absolutePath
