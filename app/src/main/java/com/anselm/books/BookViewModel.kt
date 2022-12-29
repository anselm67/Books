package com.anselm.books

import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.anselm.books.database.BookRepository

private const val PAGE_SIZE = 100
private const val MAX_SIZE = 500

class BookViewModel(
    private val repository: BookRepository
) : ViewModel() {
    val data = Pager(
        config = PagingConfig(
        pageSize = PAGE_SIZE,
        enablePlaceholders = true,
        maxSize = MAX_SIZE,
        jumpThreshold = 2 * PAGE_SIZE)
    ) {
        repository.bookPagingSource()
    }.flow.cachedIn(viewModelScope)

    companion object {
        val Factory = object: ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return BookViewModel(BooksApplication.app.repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
