package com.anselm.books

import androidx.lifecycle.*
import kotlinx.coroutines.launch

class BookViewModel(private val bookRepository: BookRepository) : ViewModel() {

    val allBooks: LiveData<List<Book>> = bookRepository.allBooks.asLiveData()

    fun insert(book: Book) = viewModelScope.launch {
        bookRepository.insert(book)
    }

}

class BookViewModelFactory(private val bookRepository: BookRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookViewModel(bookRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}