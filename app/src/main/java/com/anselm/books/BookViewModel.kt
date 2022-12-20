package com.anselm.books

import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn

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
            jumpThreshold = 2 * PAGE_SIZE)) {
        repository.bookPagingSource()
    }.flow.cachedIn(viewModelScope)
}


class BookViewModelFactory(
    private val bookRepository: BookRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookViewModel(bookRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}