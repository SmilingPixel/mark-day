package io.github.smiling_pixel.sync

internal actual suspend fun writeDiaryEntryExportFiles(
    files: List<DiaryEntryExportFile>
): DiaryEntryExportResult = DiaryEntryExportResult.Unavailable
