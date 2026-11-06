package io.github.smiling_pixel.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.MomentImportPreflight
import io.github.smiling_pixel.filesystem.importMoments
import io.github.smiling_pixel.filesystem.inspectMomentImports
import io.github.smiling_pixel.filesystem.rememberFilePicker
import io.github.smiling_pixel.model.FileMetadata
import kotlinx.coroutines.launch

/**
 * Selects existing or newly imported Moments for a diary entry.
 *
 * @param fileRepo Repository used to import new Moment files.
 * @param files Current Moment metadata.
 * @param initialSelection Moment IDs selected when the dialog opened.
 * @param onConfirm Returns the complete staged selection.
 * @param onDismiss Closes without changing the editor form.
 */
@Composable
fun EntryAttachmentPicker(
    fileRepo: FileRepository,
    files: List<FileMetadata>,
    initialSelection: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIds by remember(initialSelection) { mutableStateOf(initialSelection) }
    var importedFiles by remember { mutableStateOf<List<FileMetadata>>(emptyList()) }
    var pendingPreflight by remember { mutableStateOf<MomentImportPreflight?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val visibleFiles = remember(files, importedFiles) { (files + importedFiles).distinctBy(FileMetadata::id) }

    fun import(preflight: MomentImportPreflight) {
        pendingPreflight = null
        isImporting = true
        scope.launch {
            val result = fileRepo.importMoments(preflight)
            importedFiles = (importedFiles + result.imported).distinctBy(FileMetadata::id)
            selectedIds = selectedIds + result.imported.map(FileMetadata::id)
            status =
                if (result.failedCount + result.rejectedCount == 0) {
                    "Imported ${result.imported.size} file(s)."
                } else {
                    "Imported ${result.imported.size}; ${result.failedCount + result.rejectedCount} skipped."
                }
            isImporting = false
        }
    }

    val picker =
        rememberFilePicker { selected ->
            val preflight = inspectMomentImports(selected)
            when {
                preflight.accepted.isEmpty() -> status = "No selected files can be imported."
                preflight.requiresLargeFileConfirmation -> pendingPreflight = preflight
                else -> import(preflight)
            }
        }

    pendingPreflight?.let { preflight ->
        AlertDialog(
            onDismissRequest = { pendingPreflight = null },
            title = { Text("Import large files?") },
            text = { Text("At least one file exceeds 100 MB and may take longer to load.") },
            confirmButton = { TextButton(onClick = { import(preflight) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { pendingPreflight = null }) { Text("Cancel") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attach Moments") },
        text = {
            Column {
                Button(onClick = picker::launch, enabled = !isImporting) {
                    if (isImporting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                }
                if (visibleFiles.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text("No Moments available")
                    }
                } else {
                    Column(Modifier.heightIn(max = 360.dp)) {
                        visibleFiles.forEach { file ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIds =
                                                if (file.id in
                                                    selectedIds
                                                ) {
                                                    selectedIds - file.id
                                                } else {
                                                    selectedIds + file.id
                                                }
                                        }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MomentPreview(file, Modifier.size(48.dp))
                                Text(
                                    file.originalFileName,
                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Checkbox(checked = file.id in selectedIds, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selectedIds) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Displays linked Moments as a stable horizontal attachment strip. */
@Composable
fun EntryAttachmentStrip(
    files: List<FileMetadata>,
    editable: Boolean,
    onRemove: (Long) -> Unit = {},
) {
    if (files.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("Attachments", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = FileMetadata::id) { file ->
                Card(modifier = Modifier.width(112.dp)) {
                    Box {
                        MomentPreview(file, Modifier.fillMaxWidth().height(72.dp))
                        if (editable) {
                            IconButton(
                                onClick = { onRemove(file.id) },
                                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Unlink ${file.originalFileName}")
                            }
                        }
                    }
                    Text(
                        file.originalFileName,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
