package com.anselm.books

import android.util.Log
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

    suspend fun getTotalCount(): Int {
        return bookDao.getTotalCount()
    }

    suspend fun getPagedList(limit: Int, offset: Int): List<Book> {
        Log.d(TAG, "getPagedList [$offset, $limit] ${query.query}/${query.partial}," +
                " location: '${query.location}'," +
                " genre: '${query.genre}'" +
                " publisher: '${query.publisher}'" +
                " author: ${query.author}" +
                " sort: ${query.sortBy}"
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
                query.sortBy, limit, offset)
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
                query.sortBy, limit, offset)
        }
    }

    val itemCount = MutableLiveData(0)

    suspend fun getPagedListCount(): Int {
        Log.d(TAG, "getPagedListCount ${query.query}/${query.partial}," +
                " location: '${query.location}'," +
                " genre: '${query.genre}'" +
                " publisher: '${query.publisher}'" +
                " author: ${query.author}"
        )
        val count = if ( isEmpty(query.query) ) {
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
        itemCount.postValue(count)
        return count
    }

    suspend fun deleteAll() {
        bookDao.deleteAll()
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

    fun invalidate() {
        pagingSource?.invalidate()
    }

    /**
     * Loads a book by id.
     */
    suspend fun load(bookId: Int): Book {
        return bookDao.getBook(bookId)
    }

    suspend fun save(book: Book) {
        val timestamp = System.currentTimeMillis() / 1000
        if (book.id <= 0) {
            book.raw_dateAdded = timestamp
            book.lastModified = timestamp
            bookDao.insert(book)
        } else {
            book.lastModified = timestamp
            bookDao.update(book)
        }
    }


}