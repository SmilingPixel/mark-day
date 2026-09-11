package io.github.smiling_pixel.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.MomentExportResult
import io.github.smiling_pixel.filesystem.MomentImportPreflight
import io.github.smiling_pixel.filesystem.exportMoments
import io.github.smiling_pixel.filesystem.importMoments
import io.github.smiling_pixel.filesystem.inspectMomentImports
import io.github.smiling_pixel.filesystem.rememberFilePicker
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.model.LoadState
import io.github.smiling_pixel.model.MomentEntryLink
import io.github.smiling_pixel.util.Logger
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Displays the local media library and coordinates import, export, selection, and reversible deletion.
 *
 * @param fileRepo Repository containing Moment bytes, metadata, and entry relationships.
 * @param diaryEntries Entries used to resolve read-only relationship labels.
 * @param onOpenEntry Opens a related diary entry.
 * @param onOperationEvent Publishes snackbar and diagnostic events to the application host.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MomentsScreen(
    fileRepo: FileRepository,
    diaryEntries: List<DiaryEntry> = emptyList(),
    onOpenEntry: (String) -> Unit = {},
    onOperationEvent: (OperationEvent) -> Unit = {},
) {
    val allFiles by fileRepo.files.collectAsState(initial = emptyList())
    val links by fileRepo.links.collectAsState(initial = emptyList())
    val filesState by fileRepo.filesState.collectAsState()
    val pendingDeletionIds by fileRepo.pendingDeletionIds.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var detailFile by remember { mutableStateOf<FileMetadata?>(null) }
    var deleteFiles by remember { mutableStateOf<List<FileMetadata>>(emptyList()) }
    var pendingPreflight by remember { mutableStateOf<MomentImportPreflight?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val files = allFiles.filterNot { it.id in pendingDeletionIds }
    val linksByFile = remember(links) { links.groupBy(MomentEntryLink::fileId) }

    fun import(preflight: MomentImportPreflight) {
        pendingPreflight = null
        isImporting = true
        scope.launch {
            val result = fileRepo.importMoments(preflight)
            isImporting = false
            val message =
                buildString {
                    append("Imported ${result.imported.size} file${if (result.imported.size == 1) "" else "s"}.")
                    if (result.rejectedCount > 0) append(" ${result.rejectedCount} exceeded 500 MB.")
                    if (result.failedCount > 0) append(" ${result.failedCount} failed.")
                }
            onOperationEvent(OperationEvent(message))
        }
    }

    val picker =
        rememberFilePicker { platformFiles ->
            val preflight = inspectMomentImports(platformFiles)
            when {
                preflight.accepted.isEmpty() -> {
                    onOperationEvent(OperationEvent("No files were imported. Files must be 500 MB or smaller."))
                }
                preflight.requiresLargeFileConfirmation -> pendingPreflight = preflight
                else -> import(preflight)
            }
        }

    fun export(filesToExport: List<FileMetadata>) {
        if (filesToExport.isEmpty()) return
        scope.launch {
            when (val result = fileRepo.exportMoments(filesToExport)) {
                is MomentExportResult.Success ->
                    onOperationEvent(
                        OperationEvent("Exported ${result.exportedCount} file(s) to ${result.destinationDescription}."),
                    )
                is MomentExportResult.PartialSuccess ->
                    onOperationEvent(
                        OperationEvent(
                            "Exported ${result.exportedCount} file(s); ${result.failedCount} were unavailable.",
                        ),
                    )
                MomentExportResult.Cancelled -> Unit
                MomentExportResult.Unavailable ->
                    onOperationEvent(
                        OperationEvent("Export is unavailable on this platform."),
                    )
                is MomentExportResult.Failure -> onOperationEvent(OperationEvent(result.message))
            }
        }
    }

    fun confirmDelete(filesToDelete: List<FileMetadata>) {
        if (filesToDelete.isNotEmpty()) deleteFiles = filesToDelete
    }

    pendingPreflight?.let { preflight ->
        AlertDialog(
            onDismissRequest = { pendingPreflight = null },
            title = { Text("Import large files?") },
            text = {
                Text(
                    "At least one selected file is larger than 100 MB. Large files may take longer to load and export.",
                )
            },
            confirmButton = { TextButton(onClick = { import(preflight) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { pendingPreflight = null }) { Text("Cancel") } },
        )
    }

    if (deleteFiles.isNotEmpty()) {
        val deleteIds = deleteFiles.mapTo(mutableSetOf()) { it.id }
        val affectedEntryCount =
            links.count { it.fileId in deleteIds }.let { count ->
                links
                    .filter { it.fileId in deleteIds }
                    .map { it.entrySyncId }
                    .distinct()
                    .size
            }
        AlertDialog(
            onDismissRequest = { deleteFiles = emptyList() },
            title = { Text("Delete ${deleteFiles.size} file${if (deleteFiles.size == 1) "" else "s"}?") },
            text = {
                Text(
                    if (affectedEntryCount > 0) {
                        "This removes links from $affectedEntryCount diary entry" +
                            if (affectedEntryCount == 1) ". The entry remains." else " entries. The entries remain."
                    } else {
                        "The selected files will be removed from Moments."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val confirmed = deleteFiles
                        deleteFiles = emptyList()
                        detailFile = null
                        selectedIds = emptySet()
                        scope.launch {
                            val staged = fileRepo.stageDelete(confirmed.mapTo(mutableSetOf()) { it.id })
                            onOperationEvent(
                                OperationEvent(
                                    message = "${staged.files.size} file(s) deleted",
                                    actionLabel = "Undo",
                                    action = { fileRepo.requestUndoDelete(staged.token) },
                                    onDismiss = { fileRepo.requestFinalizeDelete(staged.token) },
                                ),
                            )
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteFiles = emptyList() }) { Text("Cancel") } },
        )
    }

    detailFile?.let { file ->
        val relatedIds = linksByFile[file.id].orEmpty().mapTo(mutableSetOf()) { it.entrySyncId }
        val relatedEntries = diaryEntries.filter { it.syncId in relatedIds }
        MomentDetailsDialog(
            file = file,
            fileRepo = fileRepo,
            relatedEntries = relatedEntries,
            onDismiss = { detailFile = null },
            onOpenEntry = {
                detailFile = null
                onOpenEntry(it)
            },
            onExport = { export(listOf(file)) },
            onDelete = { confirmDelete(listOf(file)) },
        )
    }

    Scaffold(
        floatingActionButton = {
            if (selectedIds.isEmpty()) {
                FloatingActionButton(onClick = { if (!isImporting) picker.launch() }) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "Import files")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selectedIds.isNotEmpty()) {
                MomentSelectionToolbar(
                    selectedCount = selectedIds.size,
                    allSelected = selectedIds.size == files.size,
                    onClose = { selectedIds = emptySet() },
                    onSelectAll = { selectedIds = files.mapTo(mutableSetOf()) { it.id } },
                    onExport = { export(files.filter { it.id in selectedIds }) },
                    onDelete = { confirmDelete(files.filter { it.id in selectedIds }) },
                )
            }
            when (val state = filesState) {
                LoadState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is LoadState.Error ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                is LoadState.Content -> {
                    if (files.isEmpty()) {
                        MomentEmptyState(isImporting = isImporting, onImport = picker::launch)
                    } else {
                        MomentGrid(
                            files = files,
                            linksByFile = linksByFile,
                            selectedIds = selectedIds,
                            onClick = { file ->
                                if (selectedIds.isEmpty()) {
                                    detailFile = file
                                } else {
                                    selectedIds = selectedIds.toggle(file.id)
                                }
                            },
                            onLongClick = { file -> selectedIds = selectedIds + file.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentSelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close selection") }
        Text("$selectedCount selected", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        if (!allSelected) {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.CheckCircle, contentDescription = "Select all") }
        }
        IconButton(onClick = onExport) { Icon(Icons.Default.Share, contentDescription = "Export selected") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete selected") }
    }
}

@Composable
private fun MomentEmptyState(
    isImporting: Boolean,
    onImport: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(56.dp))
        Text("Your Moments will appear here", style = MaterialTheme.typography.headlineSmall)
        Text("Keep photos and files with your diary.", modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onImport, enabled = !isImporting, modifier = Modifier.padding(top = 20.dp)) {
            Text("Import files")
        }
    }
}

@Composable
private fun MomentGrid(
    files: List<FileMetadata>,
    linksByFile: Map<Long, List<MomentEntryLink>>,
    selectedIds: Set<Long>,
    onClick: (FileMetadata) -> Unit,
    onLongClick: (FileMetadata) -> Unit,
) {
    val grouped = remember(files) { files.sortedByDescending { it.createdAt }.groupBy { it.createdAt.toMonthYear() } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(144.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        grouped.forEach { (month, monthFiles) ->
            item(key = "header-$month", span = { GridItemSpan(maxLineSpan) }) {
                Text(month, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(monthFiles, key = FileMetadata::id) { file ->
                MomentTile(
                    file = file,
                    relatedCount = linksByFile[file.id].orEmpty().size,
                    selected = file.id in selectedIds,
                    onClick = { onClick(file) },
                    onLongClick = { onLongClick(file) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MomentTile(
    file: FileMetadata,
    relatedCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
    ) {
        Box {
            MomentPreview(file, Modifier.fillMaxWidth().height(112.dp))
            if (selected) {
                Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(file.originalFileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                formatBytes(file.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMomentDate(file.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (relatedCount > 0) {
                Text(
                    "$relatedCount related ${if (relatedCount == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Displays an image thumbnail or a stable file-category fallback. */
@Composable
fun MomentPreview(
    file: FileMetadata,
    modifier: Modifier = Modifier,
) {
    var decodeFailed by remember(file.filePath) { mutableStateOf(false) }
    var failureLogged by remember(file.filePath) { mutableStateOf(false) }
    val kind = momentKind(file)
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (kind == MomentKind.IMAGE && !decodeFailed) {
            AsyncImage(
                model = "localfile:///${file.filePath}",
                contentDescription = file.originalFileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    decodeFailed = true
                    if (!failureLogged) {
                        failureLogged = true
                        Logger.w("MomentsThumbnail", "decode_failed")
                    }
                },
            )
        } else {
            Icon(
                imageVector = if (decodeFailed) Icons.Default.Warning else momentIcon(kind),
                contentDescription = if (decodeFailed) "Preview unavailable" else kind.description,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MomentDetailsDialog(
    file: FileMetadata,
    fileRepo: FileRepository,
    relatedEntries: List<DiaryEntry>,
    onDismiss: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val exists by produceState<Boolean?>(initialValue = null, file.filePath) {
        value = runCatching { fileRepo.fileExists(file) }.getOrDefault(false)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.originalFileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MomentPreview(file, Modifier.fillMaxWidth().height(180.dp))
                Text("Size: ${formatBytes(file.sizeBytes)}")
                Text("Added: ${formatMomentDate(file.createdAt)}")
                Text("Type: ${file.mimeType}")
                when (exists) {
                    null -> Text("Checking file…")
                    false -> Text("File missing", color = MaterialTheme.colorScheme.error)
                    true -> Unit
                }
                if (relatedEntries.isNotEmpty()) {
                    Text("Related entries", fontWeight = FontWeight.SemiBold)
                    relatedEntries.forEach { entry ->
                        TextButton(onClick = { onOpenEntry(entry.syncId) }) {
                            Text(entry.title.ifBlank { "Untitled entry · ${entry.entryDate}" }, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onExport, enabled = exists == true) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

internal enum class MomentKind(
    val description: String,
) {
    IMAGE("Image file"),
    PDF("PDF document"),
    DOCUMENT("Document file"),
    AUDIO("Audio file"),
    VIDEO("Video file"),
    ARCHIVE("Archive file"),
    GENERIC("File"),
}

internal fun momentKind(file: FileMetadata): MomentKind =
    when {
        file.mimeType.startsWith("image/") && file.mimeType != "image/svg+xml" -> MomentKind.IMAGE
        file.mimeType == "application/pdf" -> MomentKind.PDF
        file.mimeType.startsWith("text/") -> MomentKind.DOCUMENT
        file.mimeType.startsWith("audio/") -> MomentKind.AUDIO
        file.mimeType.startsWith("video/") -> MomentKind.VIDEO
        file.mimeType in
            setOf(
                "application/zip",
                "application/x-rar-compressed",
                "application/gzip",
            )
        -> MomentKind.ARCHIVE
        else -> MomentKind.GENERIC
    }

private fun momentIcon(kind: MomentKind) =
    when (kind) {
        MomentKind.PDF -> Icons.Default.PictureAsPdf
        MomentKind.DOCUMENT -> Icons.Default.Description
        MomentKind.AUDIO -> Icons.Default.Audiotrack
        MomentKind.VIDEO -> Icons.Default.Movie
        MomentKind.ARCHIVE -> Icons.Default.Archive
        MomentKind.IMAGE, MomentKind.GENERIC -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

/** Formats an epoch-millisecond timestamp as a local month and year. */
fun Long.toMonthYear(): String {
    val dateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.month.name.lowercase().replaceFirstChar(Char::uppercase)} ${dateTime.year}"
}

internal fun formatMomentDate(epochMilliseconds: Long): String {
    val value = Instant.fromEpochMilliseconds(epochMilliseconds).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.date} ${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id
