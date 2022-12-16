package com.anselm.books

import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData

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
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getFilteredPagedList(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!,
                limit, offset)
        } else /* Requests text matching. */ {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getTitlePagedList(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!,
                limit, offset)
        }
    }

    val itemCount = MutableLiveData(0)

    suspend fun getPagedListCount(): Int {
        Log.d(TAG, "runQuery ${query.query}/${query.partial}," +
                " location: '${query.location}'," +
                " genre: '${query.genre}'" +
                " publisher: '${query.publisher}'"
        )
        itemCount.value = if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getFilteredPagedListCount(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* Requests text matching. */ {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getTitlePagedListCount(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
        return itemCount.value!!
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
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getFilteredPhysicalLocation(
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* Requests text match. */ {
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getTitlePhysicalLocation(
                if (query.partial) query.query!! + '*' else query.query!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
    }

    suspend fun getGenres(): List<Histo> {
        return if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            val isPublisherEmpty = isEmpty(query.publisher)
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getFilteredGenre(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = isEmpty(query.location)
            val isPublisherEmpty = isEmpty(query.publisher)
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getTitleGenre(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
    }

    suspend fun getPublishers(): List<Histo> {
        return if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getFilteredPublisher(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isAuthorEmpty = isEmpty(query.author)
            bookDao.getTitlePublisher(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
    }

    suspend fun getAuthors(): List<Histo> {
        return if ( isEmpty(query.query) ) {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getFilteredAuthor(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = isEmpty(query.location)
            val isGenreEmpty = isEmpty(query.genre)
            val isPublisherEmpty = isEmpty(query.publisher)
            bookDao.getTitleAuthor(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
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