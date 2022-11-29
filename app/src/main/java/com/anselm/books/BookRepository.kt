package com.anselm.books

import androidx.annotation.WorkerThread

class BookRepository(private val bookDao: BookDao) {

    // TODO()
    // to the dao in a so it can generate the right text search query (fts4)
    // The bookPagingSource() function will get called because of the refresh() in
    // HomeFragment::onQueryTextSubmit, and can create a new BookPagingSource that
    // uses the getFilteredPageList method
    fun bookPagingSource() = BookPagingSource(this)

    suspend fun getPagedList(limit: Int, offset: Int): List<Book> {
        return bookDao.getPagedList(limit, offset)
    }

    suspend fun deleteAll() {
        bookDao.deleteAll()
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(book: Book) {
        bookDao.insert(book)
    }
}