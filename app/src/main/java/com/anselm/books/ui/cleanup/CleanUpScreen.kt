package com.anselm.books.ui.cleanup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.database.Label
import com.anselm.books.database.Query

private data class CleanUpState(
    val loaded: Boolean = false,
    val totalBooks: Int = 0,
    val duplicateBooks: Int = 0,
    val booksWithoutCover: Int = 0,
    val booksWithoutAuthors: Int = 0,
    val booksWithoutGenres: Int = 0,
    val booksWithoutLocations: Int = 0,
    val booksWithoutLanguages: Int = 0,
    val labelCounts: Map<Label.Type, Int> = emptyMap(),
)

@Composable
fun CleanUpScreen(
    onNavigateToPager: (Query) -> Unit,
    onNavigateToSearch: (Query) -> Unit,
    onNavigateToLabelCleanup: (Label.Type) -> Unit,
    onCheckImages: () -> Unit,
    onDeleteUnusedImages: () -> Unit,
) {
    var state by remember { mutableStateOf(CleanUpState()) }

    LaunchedEffect(Unit) {
        app.repository.deleteUnusedLabels()
        val labelTypeCounts = app.repository.getLabelTypeCounts()
        state = CleanUpState(
            loaded = true,
            totalBooks = app.repository.getTotalCount(),
            duplicateBooks = app.repository.getDuplicateBookCount(),
            booksWithoutCover = app.repository.getWithoutCoverBookCount(),
            booksWithoutAuthors = app.repository.getWithoutLabelBookCount(Label.Type.Authors),
            booksWithoutGenres = app.repository.getWithoutLabelBookCount(Label.Type.Genres),
            booksWithoutLocations = app.repository.getWithoutLabelBookCount(Label.Type.Location),
            booksWithoutLanguages = app.repository.getWithoutLabelBookCount(Label.Type.Language),
            labelCounts = labelTypeCounts.associate { it.type to it.count },
        )
    }

    if (!state.loaded) {
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {

        item { SectionHeader(stringResource(R.string.book_count, state.totalBooks)) }
        if (state.duplicateBooks > 0) {
            item {
                CleanUpItem(stringResource(R.string.duplicate_books_cleanup, state.duplicateBooks)) {
                    onNavigateToPager(Query(type = Query.Type.Duplicates))
                }
            }
        }
        if (state.booksWithoutCover > 0) {
            item {
                CleanUpItem(stringResource(R.string.without_cover_books_cleanup, state.booksWithoutCover)) {
                    onNavigateToPager(Query(type = Query.Type.NoCover))
                }
            }
        }
        if (state.booksWithoutAuthors > 0) {
            item {
                CleanUpItem(stringResource(R.string.without_authors_cleanup, state.booksWithoutAuthors)) {
                    onNavigateToSearch(Query(withoutLabelOfType = Label.Type.Authors))
                }
            }
        }
        if (state.booksWithoutGenres > 0) {
            item {
                CleanUpItem(stringResource(R.string.without_genres_cleanup, state.booksWithoutGenres)) {
                    onNavigateToSearch(Query(withoutLabelOfType = Label.Type.Genres))
                }
            }
        }
        if (state.booksWithoutLocations > 0) {
            item {
                CleanUpItem(stringResource(R.string.without_locations_cleanup, state.booksWithoutLocations)) {
                    onNavigateToSearch(Query(withoutLabelOfType = Label.Type.Location))
                }
            }
        }
        if (state.booksWithoutLanguages > 0) {
            item {
                CleanUpItem(stringResource(R.string.without_languages_cleanup, state.booksWithoutLanguages)) {
                    onNavigateToSearch(Query(withoutLabelOfType = Label.Type.Language))
                }
            }
        }

        item { SectionHeader(stringResource(R.string.labels_cleanup_header)) }
        listOf(
            Pair(R.string.authors_cleanup, Label.Type.Authors),
            Pair(R.string.genres_cleanup, Label.Type.Genres),
            Pair(R.string.publishers_cleanup, Label.Type.Publisher),
            Pair(R.string.languages_cleanup, Label.Type.Language),
            Pair(R.string.locations_cleanup, Label.Type.Location),
        ).forEach { (stringId, type) ->
            item {
                CleanUpItem(stringResource(stringId, state.labelCounts[type] ?: 0)) {
                    onNavigateToLabelCleanup(type)
                }
            }
        }

        item { SectionHeader(stringResource(R.string.cleanup_book_cover_section)) }
        item { CleanUpItem(stringResource(R.string.check_for_broken_images)) { onCheckImages() } }
        item { CleanUpItem(stringResource(R.string.check_gc_images)) { onDeleteUnusedImages() } }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun CleanUpItem(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_arrow_right_24),
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
