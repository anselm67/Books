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
                " genre: '${query.genre}'" +
                " publisher: '${query.publisher}'"
        )
        return if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getFilteredPagedList(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                limit, offset)
        } else /* Requests text matching. */ {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getTitlePagedList(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                limit, offset)
        }
    }

    suspend fun getPagedListCount(): Int {
        Log.d(TAG, "runQuery ${query.query}/${query.partial}," +
                " location: '${query.location}'," +
                " genre: '${query.genre}'" +
                " publisher: '${query.publisher}'"
        )
        return if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getFilteredPagedListCount(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        } else /* Requests text matching. */ {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getTitlePagedListCount(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        }
    }

    suspend fun deleteAll() {
        bookDao.deleteAll()
    }

    suspend fun getBook(bookId: Int): Book {
        return bookDao.getBook(bookId)
    }

    suspend fun getLocations(): List<Histo> {
        return if ( isEmpty(query.query) ) {
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getFilteredPhysicalLocation(
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        } else /* Requests text match. */ {
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getTitlePhysicalLocation(
                if (query.partial) query.query!! + '*' else query.query!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        }
    }

    suspend fun getGenres(): List<Histo> {
        return if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getFilteredGenre(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = isEmpty(query.location)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getTitleGenre(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        }
    }

    suspend fun getPublishers(): List<Histo> {
        return if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            bookDao.getFilteredPublisher(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            bookDao.getTitlePublisher(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!)
        }
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