package com.anselm.books

import android.util.Log
import androidx.annotation.WorkerThread

class BookRepository(private val bookDao: BookDao) {
    private var pagingSource: BookPagingSource? = null

    var titleQuery: String? = null
        set(value) {
            field = if (value?.trim() == "") null else value
            Log.d(TAG, "Setting titleQuery to $value.")
            pagingSource?.invalidate()
        }

    fun bookPagingSource() : BookPagingSource {
        pagingSource = BookPagingSource(this)
        return pagingSource!!
    }

    suspend fun getPagedList(limit: Int, offset: Int): List<Book> {
        return if (titleQuery == null)
            bookDao.getAllPagedList(limit, offset)
        else
            bookDao.getTitlePagedList(titleQuery!!, limit, offset)
    }

    suspend fun deleteAll() {
        bookDao.deleteAll()
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