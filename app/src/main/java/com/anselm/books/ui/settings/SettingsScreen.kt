package com.anselm.books.ui.settings

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.BooksPreferences
import com.anselm.books.R
import com.anselm.books.TAG
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.core.content.edit

private fun collectStats(): Map<String, Triple<Int, Int, Int>> =
    buildMap { app.lookupService.clientKeys { put(it, app.lookupService.stats(it)) } }

@Composable
fun SettingsScreen() {
    val prefs = app.prefs

    val showResetDbDialog = remember { mutableStateOf(false) }
    val showResetStatsDialog = remember { mutableStateOf(false) }
    val showSortDialog = remember { mutableStateOf(false) }
    val showOclcDialog = remember { mutableStateOf(false) }

    val importLauncher = run {
        val startingImporting = stringResource(R.string.starting_importing)
        val importFailed = stringResource(R.string.import_failed)
        val importStatus = stringResource(R.string.import_status)
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) {
                app.toast(R.string.select_import_file_prompt)
            } else {
                var counts = Pair(-1, -1); var msg: String? = null
                val reporter = app.openReporter(startingImporting, isIndeterminate = false)
                app.applicationScope.launch {
                    try { counts = app.importExport.importZipFile(uri, reporter) }
                    catch (e: Exception) { Log.e(TAG, "Import failed.", e); msg = e.message }
                }.invokeOnCompletion {
                    reporter.close()
                    app.toast(if (msg != null) importFailed.format(msg)
                              else importStatus.format(counts.first, counts.second))
                }
            }
        }
    }

    val exportLauncher = run {
        val exportingBooks = stringResource(R.string.exporting_books)
        val exportFailed = stringResource(R.string.export_failed)
        val exportStatus = stringResource(R.string.export_status)
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            if (uri == null) {
                app.toast("Select a file to export to.")
            } else {
                var count = 0; var msg: String? = null
                val reporter = app.openReporter(exportingBooks, isIndeterminate = false)
                app.applicationScope.launch {
                    try { count = app.importExport.exportZipFile(uri, reporter) }
                    catch (e: Exception) { Log.e(TAG, "Export failed.", e); msg = e.message }
                }.invokeOnCompletion {
                    reporter.close()
                    app.toast(if (msg != null) exportFailed.format(msg)
                              else exportStatus.format(count))
                }
            }
        }
    }

    // Import options
    var useLastLocation by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.USE_LAST_LOCATION, true)) }
    val lastLocationValue by remember { mutableStateOf(prefs.getString(BooksPreferences.USE_LAST_LOCATION_VALUE, "") ?: "") }
    var useOnlyExistingGenres by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.USE_ONLY_EXISTING_GENRES, false)) }

    // Lookup services
    var useGoogle by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.USE_GOOGLE, true)) }
    var useBNF by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.USE_BNF, true)) }
    var useWorldcat by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.USE_WORLDCAT, true)) }
    var useiTunes by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.USE_ITUNES, true)) }
    var useAmazon by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.USE_AMAZON, true)) }
    var useOpenLibrary by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.USE_OPEN_LIBRARY, true)) }
    var oclcKey by remember { mutableStateOf(prefs.getString(BooksPreferences.OCLC_KEY, "") ?: "") }
    var stats by remember { mutableStateOf(collectStats()) }

    // Display
    val sortIds = listOf("LastAdded", "Alphabetical")
    val sortNames = listOf("Last Added", "Alphabetical (title, author)")
    var sortOrder by remember { mutableStateOf(prefs.getString(BooksPreferences.SORT_ORDER, "LastAdded") ?: "LastAdded") }
    var enableShortcutToEdit by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.ENABLE_SHORTCUT_TO_EDIT, true)) }
    var displayBookId by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.DISPLAY_BOOK_ID, false)) }
    var displayLastModified by remember { mutableStateOf(prefs.getBoolean(BooksPreferences.DISPLAY_LAST_MODIFIED, false)) }

    fun boolPref(key: String, v: Boolean) = prefs.edit { putBoolean(key, v) }
    fun strPref(key: String, v: String) = prefs.edit { putString(key, v) }

    val statsTemplate = stringResource(R.string.preferences_lookup_service_stats)
    val statsSummary: (String) -> String? = { key ->
        stats[key]?.let { (l, m, c) ->
            statsTemplate.format(l, m, c)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {

        item { SectionHeader("Import / Export") }
        item { ClickPref(stringResource(R.string.import_preference_title)) { importLauncher.launch("*/*") } }
        item {
            ClickPref(stringResource(R.string.export_preference_title)) {
                exportLauncher.launch("${DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())} - books.zip")
            }
        }
        item { ClickPref(stringResource(R.string.reset_preference_title)) {
            showResetDbDialog.value = true }
        }

        item { SectionHeader(stringResource(R.string.import_preferences_header)) }
        item {
            CheckPref(
                title = stringResource(R.string.lookup_use_last_location_prompt),
                subtitle = if (useLastLocation) lastLocationValue.ifEmpty { null } else null,
                checked = useLastLocation,
            ) { v -> useLastLocation = v; boolPref(BooksPreferences.USE_LAST_LOCATION, v) }
        }
        item {
            CheckPref(
                title = stringResource(R.string.lookup_use_only_existing_genres_prompt),
                checked = useOnlyExistingGenres,
            ) { v -> useOnlyExistingGenres = v; boolPref(BooksPreferences.USE_ONLY_EXISTING_GENRES, v) }
        }

        item { SectionHeader(stringResource(R.string.preference_category_lookup_service)) }
        item {
            CheckPref("Google Books", subtitle = statsSummary(BooksPreferences.USE_GOOGLE), checked = useGoogle) {
                v -> useGoogle = v; boolPref(BooksPreferences.USE_GOOGLE, v)
            }
        }
        item {
            CheckPref("Bibliothèque Nationale de France", subtitle = statsSummary(BooksPreferences.USE_BNF), checked = useBNF) {
                v -> useBNF = v; boolPref(BooksPreferences.USE_BNF, v)
            }
        }
        item {
            CheckPref(
                title = "Worldcat",
                subtitle = if (oclcKey.isEmpty()) stringResource(R.string.worldcat_requires_wskey)
                           else statsSummary(BooksPreferences.USE_WORLDCAT),
                checked = useWorldcat,
                enabled = oclcKey.isNotEmpty(),
            ) { v -> useWorldcat = v; boolPref(BooksPreferences.USE_WORLDCAT, v) }
        }
        item {
            CheckPref("iTunes", subtitle = statsSummary(BooksPreferences.USE_ITUNES), checked = useiTunes) {
                v -> useiTunes = v; boolPref(BooksPreferences.USE_ITUNES, v)
            }
        }
        item {
            CheckPref("Amazon", subtitle = statsSummary(BooksPreferences.USE_AMAZON), checked = useAmazon) {
                v -> useAmazon = v; boolPref(BooksPreferences.USE_AMAZON, v)
            }
        }
        item {
            CheckPref("Open Library", subtitle = statsSummary(BooksPreferences.USE_OPEN_LIBRARY), checked = useOpenLibrary) {
                v -> useOpenLibrary = v; boolPref(BooksPreferences.USE_OPEN_LIBRARY, v)
            }
        }
        item { ClickPref("Reset lookup statistics.") { showResetStatsDialog.value = true } }
        item {
            ClickPref(
                title = "Worldcat / OCLC wskey",
                subtitle = oclcKey.ifEmpty { null },
            ) { showOclcDialog.value = true }
        }

        item { SectionHeader(stringResource(R.string.preference_category_display)) }
        item {
            ClickPref(
                title = stringResource(R.string.sort_order_preference_prompt),
                subtitle = sortNames.getOrElse(sortIds.indexOf(sortOrder)) { sortNames[0] },
            ) { showSortDialog.value = true }
        }
        item {
            CheckPref(stringResource(R.string.preference_enable_shortcut_to_edit), checked = enableShortcutToEdit) {
                v -> enableShortcutToEdit = v; boolPref(BooksPreferences.ENABLE_SHORTCUT_TO_EDIT, v)
            }
        }
        item {
            CheckPref(stringResource(R.string.preference_display_book_id), checked = displayBookId) {
                v -> displayBookId = v; boolPref(BooksPreferences.DISPLAY_BOOK_ID, v)
            }
        }
        item {
            CheckPref(stringResource(R.string.preference_display_last_modified), checked = displayLastModified) {
                v -> displayLastModified = v; boolPref(BooksPreferences.DISPLAY_LAST_MODIFIED, v)
            }
        }
    }

    if (showResetDbDialog.value) {
        AlertDialog(
            onDismissRequest = { showResetDbDialog.value = false },
            text = { Text(stringResource(R.string.reset_database_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDbDialog.value = false
                    app.applicationScope.launch { app.repository.deleteAll() }
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDbDialog.value = false }) { Text(stringResource(R.string.no)) }
            }
        )
    }

    if (showResetStatsDialog.value) {
        AlertDialog(
            onDismissRequest = { showResetStatsDialog.value = false },
            title = { Text("Really reset lookup statistics?") },
            confirmButton = {
                TextButton(onClick = {
                    showResetStatsDialog.value = false
                    app.lookupService.resetStats()
                    stats = collectStats()
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetStatsDialog.value = false }) { Text(stringResource(R.string.no)) }
            }
        )
    }

    if (showSortDialog.value) {
        AlertDialog(
            onDismissRequest = { showSortDialog.value = false },
            title = { Text(stringResource(R.string.sort_order_preference_prompt)) },
            text = {
                Column {
                    sortIds.forEachIndexed { i, id ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sortOrder = id
                                    strPref(BooksPreferences.SORT_ORDER, id)
                                    showSortDialog.value = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = sortOrder == id,
                                onClick = {
                                    sortOrder = id
                                    strPref(BooksPreferences.SORT_ORDER, id)
                                    showSortDialog.value = false
                                },
                            )
                            Text(sortNames[i], style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showOclcDialog.value) {
        var editText by remember { mutableStateOf(oclcKey) }
        AlertDialog(
            onDismissRequest = { showOclcDialog.value = false },
            title = { Text("Worldcat / OCLC wskey") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    oclcKey = editText
                    strPref(BooksPreferences.OCLC_KEY, editText)
                    showOclcDialog.value = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showOclcDialog.value = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun ClickPref(title: String, subtitle: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Column {
                Text(
                    text = title,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun CheckPref(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Column {
                Text(
                    text = title,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = {
            Checkbox(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled)
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
