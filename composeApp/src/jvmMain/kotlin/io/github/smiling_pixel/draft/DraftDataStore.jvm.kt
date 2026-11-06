package io.github.smiling_pixel.draft

import java.io.File

internal actual fun produceDraftPath(): String =
    File(System.getProperty("user.home"), DRAFT_DATA_STORE_FILE_NAME).absolutePath
