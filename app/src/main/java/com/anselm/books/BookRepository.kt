package com.anselm.books

import androidx.annotation.WorkerThread

class BookRepository(private val bookDao: BookDao) {

    fun bookPagingSource() = BookPagingSource(this)

    suspend fun getPagedList(limit: Int, offset: Int): List<Book> {
        return bookDao.getPagedList(limit, offset)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(book: Book) {
        bookDao.insert(book)
    }
}