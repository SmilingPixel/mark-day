package io.github.smiling_pixel.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.smiling_pixel.client.WeatherClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.draft.EditorExitGuard
import io.github.smiling_pixel.draft.EntryDraftRepository
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.model.LoadState
import io.github.smiling_pixel.search.EntrySearchCriteria
import io.github.smiling_pixel.search.EntrySortField
import io.github.smiling_pixel.search.SearchTextPreview
import io.github.smiling_pixel.search.buildSearchPreview
import io.github.smiling_pixel.search.findCaseInsensitiveMatches
import io.github.smiling_pixel.search.searchEntries
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Displays entry search controls, results, and details for the selected result.
 *
 * Search form values and the applied result set remain saveable while a result is open, allowing Back to restore the
 * same search session.
 *
 * @param repo Repository whose current in-memory entry snapshot is searched.
 * @param draftRepository Repository containing device-local editor drafts.
 * @param weatherClient Client used by the entry details editor.
 * @param selectedEntrySyncId Stable ID of the result currently being viewed, or null for the result list.
 * @param onSelectedEntryChange Updates the result currently being viewed.
 * @param isSyncing Whether a cloud synchronization operation is running.
 * @param onSyncRequest Requests cloud synchronization from an opened result.
 * @param onExitGuardChange Reports the opened editor's exit protection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repo: DiaryRepository,
    draftRepository: EntryDraftRepository,
    weatherClient: WeatherClient,
    selectedEntrySyncId: String?,
    onSelectedEntryChange: (String?) -> Unit,
    isSyncing: Boolean = false,
    onSyncRequest: () -> Unit = {},
    onExitGuardChange: (EditorExitGuard?) -> Unit = {},
) {
    val entries by repo.entries.collectAsState()
    val entriesLoadState by repo.entriesState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var startDateText by rememberSaveable { mutableStateOf<String?>(null) }
    var endDateText by rememberSaveable { mutableStateOf<String?>(null) }
    var sortFieldName by rememberSaveable { mutableStateOf(EntrySortField.DIARY_DATE.name) }
    var appliedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var appliedStartDateText by rememberSaveable { mutableStateOf<String?>(null) }
    var appliedEndDateText by rememberSaveable { mutableStateOf<String?>(null) }
    var appliedSortFieldName by rememberSaveable { mutableStateOf(EntrySortField.DIARY_DATE.name) }
    var showStartDatePicker by rememberSaveable { mutableStateOf(false) }
    var showEndDatePicker by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var recentlyCommittedEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    val listState = rememberLazyListState()

    val startDate = startDateText?.let(LocalDate::parse)
    val endDate = endDateText?.let(LocalDate::parse)
    val sortField = EntrySortField.valueOf(sortFieldName)
    val formCriteria = EntrySearchCriteria(query, startDate, endDate, sortField)
    val appliedCriteria =
        appliedQuery?.let {
            EntrySearchCriteria(
                query = it,
                startDate = appliedStartDateText?.let(LocalDate::parse),
                endDate = appliedEndDateText?.let(LocalDate::parse),
                sortField = EntrySortField.valueOf(appliedSortFieldName),
            )
        }
    val results =
        remember(entries, appliedCriteria) {
            appliedCriteria?.let { searchEntries(entries, it) }.orEmpty()
        }
    val selectedEntry =
        recentlyCommittedEntry?.takeIf { it.syncId == selectedEntrySyncId }
            ?: entries.firstOrNull { it.syncId == selectedEntrySyncId }

    LaunchedEffect(entries, recentlyCommittedEntry) {
        if (recentlyCommittedEntry != null && entries.any { it == recentlyCommittedEntry }) {
            recentlyCommittedEntry = null
        }
    }

    fun applySearch() {
        if (!formCriteria.hasValidDateRange) return
        appliedQuery = formCriteria.normalizedQuery
        appliedStartDateText = startDateText
        appliedEndDateText = endDateText
        appliedSortFieldName = sortFieldName
    }

    fun clearSearch() {
        query = ""
        startDateText = null
        endDateText = null
        sortFieldName = EntrySortField.DIARY_DATE.name
        appliedQuery = null
        appliedStartDateText = null
        appliedEndDateText = null
        appliedSortFieldName = EntrySortField.DIARY_DATE.name
        sortMenuExpanded = false
    }

    if (showStartDatePicker) {
        SearchDatePickerDialog(
            initialDate = startDate,
            onDateSelected = {
                startDateText = it.toString()
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false },
        )
    }
    if (showEndDatePicker) {
        SearchDatePickerDialog(
            initialDate = endDate,
            onDateSelected = {
                endDateText = it.toString()
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false },
        )
    }

    if (selectedEntrySyncId != null) {
        if (selectedEntry == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            EntryDetailsScreen(
                entry = selectedEntry,
                weatherClient = weatherClient,
                isSyncing = isSyncing,
                onSyncRequest = onSyncRequest,
                draftRepository = draftRepository,
                onExitGuardChange = onExitGuardChange,
                onSave = { entry ->
                    repo.update(entry)
                    recentlyCommittedEntry = entry
                    entry
                },
                onCancel = {
                    recentlyCommittedEntry = null
                    onSelectedEntryChange(null)
                    onExitGuardChange(null)
                },
            )
        }
        return
    }

    if (entriesLoadState is LoadState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (entriesLoadState is LoadState.Error) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text((entriesLoadState as LoadState.Error).message, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search entries") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { applySearch() }),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchDateButton(
                label = "Start date",
                value = startDateText ?: "Any",
                onClick = { showStartDatePicker = true },
                modifier = Modifier.weight(1f),
            )
            SearchDateButton(
                label = "End date",
                value = endDateText ?: "Any",
                onClick = { showEndDatePicker = true },
                modifier = Modifier.weight(1f),
            )
        }

        if (!formCriteria.hasValidDateRange) {
            Text(
                text = "Start date must be on or before end date.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        text = sortField.displayLabel,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    EntrySortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(field.displayLabel) },
                            onClick = {
                                sortFieldName = field.name
                                sortMenuExpanded = false
                            },
                        )
                    }
                }
            }
            Button(onClick = { applySearch() }, enabled = formCriteria.hasValidDateRange) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search")
            }
            TextButton(
                onClick = { clearSearch() },
                enabled =
                    query.isNotEmpty() || startDateText != null || endDateText != null ||
                        sortField != EntrySortField.DIARY_DATE || appliedCriteria != null,
            ) {
                Text("Clear")
            }
        }

        if (appliedCriteria != null) {
            Text(
                text = if (results.size == 1) "1 result" else "${results.size} results",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider()

            if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val queryText = appliedCriteria.normalizedQuery
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (queryText.isNotEmpty()) "No entries match \"$queryText\"" else "No entries match your filters",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = { clearSearch() }) { Text("Clear filters") }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.syncId }) { entry ->
                        SearchResultCard(
                            entry = entry,
                            query = appliedCriteria.normalizedQuery,
                            onClick = { onSelectedEntryChange(entry.syncId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDateButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.DateRange, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDatePickerDialog(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun SearchResultCard(
    entry: DiaryEntry,
    query: String,
    onClick: () -> Unit,
) {
    val displayTitle = entry.title.ifBlank { "Untitled" }
    val titlePreview =
        remember(displayTitle, query) {
            SearchTextPreview(displayTitle, findCaseInsensitiveMatches(displayTitle, query))
        }
    val contentPreview = remember(entry.content, query) { buildSearchPreview(entry.content, query) }
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = titlePreview.toAnnotatedString(highlightColor),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (contentPreview.text.isNotEmpty()) {
                Text(
                    text = contentPreview.toAnnotatedString(highlightColor),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Date: ${entry.entryDate}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val EntrySortField.displayLabel: String
    get() =
        when (this) {
            EntrySortField.DIARY_DATE -> "Diary date"
            EntrySortField.CREATED_AT -> "Creation time"
            EntrySortField.UPDATED_AT -> "Recently updated"
        }

private fun SearchTextPreview.toAnnotatedString(highlightColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        matches.forEach { match ->
            addStyle(
                SpanStyle(background = highlightColor),
                start = match.start,
                end = match.endExclusive,
            )
        }
    }
