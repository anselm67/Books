package com.anselm.books

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {

    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(book: Book) {
        bookDao.insert(book)
    }
}