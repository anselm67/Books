package com.anselm.books

import androidx.annotation.WorkerThread

class BookRepository(private val bookDao: BookDao) {
    private var pagingSource: BookPagingSource? = null

    var titleQuery: String? = null
        set(value) {
            field = if (value?.trim() == "") null else value
            pagingSource?.invalidate()
        }
    var physicalLocation: String? = null
        set(value) {
            field = if (value?.trim() == "") null else value
            pagingSource?.invalidate()
        }

    fun bookPagingSource() : BookPagingSource {
        pagingSource = BookPagingSource(this)
        return pagingSource!!
    }

    suspend fun getPagedList(limit: Int, offset: Int): List<Book> {
        return if (titleQuery == null && physicalLocation == null) {
            bookDao.getAllPagedList(limit, offset)
        } else {
            val emptyTitle = (titleQuery == null || titleQuery == "")
            val emptyLocation = (physicalLocation == null || physicalLocation == "")
            bookDao.getTitlePagedList(
                emptyTitle, if (emptyTitle) "" else titleQuery!!,
                emptyLocation, if (emptyLocation) "" else physicalLocation!!,
                limit, offset
            )
        }
    }

    suspend fun deleteAll() {
        bookDao.deleteAll()
    }

    suspend fun getBook(bookId: Int): Book {
        return bookDao.getBook(bookId)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(book: Book) {
        bookDao.insert(book)
    }

    fun invalidate() {
        pagingSource?.invalidate()
    }
}