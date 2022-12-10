package com.anselm.books

import android.util.Log
import androidx.annotation.WorkerThread

class BookRepository(private val bookDao: BookDao) {
    private var pagingSource: BookPagingSource? = null
    var query = Query()
        set(value) {
            field = value
            pagingSource?.invalidate()
        }

    fun bookPagingSource() : BookPagingSource {
        pagingSource = BookPagingSource(this)
        return pagingSource!!
    }

    private fun isEmpty(s: String?): Boolean {
        return s == null || s == ""
    }

    suspend fun getPagedList(limit: Int, offset: Int): List<Book> {
        Log.d(TAG, "runQuery ${query.query}/${query.partial}," +
                " location: '${query.location}'," +
                " genre: '${query.genre}'"
        )
        return if (isEmpty(query.query) && isEmpty(query.location)) {
            bookDao.getAllPagedList(limit, offset)
        } else if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            bookDao.getFilteredPagedList(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                limit, offset)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = isEmpty(query.location)
            bookDao.getTitlePagedList(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                limit, offset)
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