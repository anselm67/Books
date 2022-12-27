package com.anselm.books.database

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.anselm.books.Book
import com.anselm.books.BookPagingSource
import com.anselm.books.TAG

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

    fun invalidate() {
        pagingSource?.invalidate()
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
        return if ( query.query.isNullOrEmpty() ) {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            bookDao.getFilteredPagedList(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!,
                query.sortBy, limit, offset)
        } else /* Requests text matching. */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
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
        val count = if ( query.query.isNullOrEmpty() ) {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            bookDao.getFilteredPagedListCount(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* Requests text matching. */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
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
        return if ( query.query.isNullOrEmpty() ) {
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            bookDao.getFilteredPhysicalLocation(
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* Requests text match. */ {
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            bookDao.getTitlePhysicalLocation(
                if (query.partial) query.query!! + '*' else query.query!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
    }

    suspend fun getGenres(): List<Histo> {
        return if ( query.query.isNullOrEmpty() ) {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            bookDao.getFilteredGenre(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            bookDao.getTitleGenre(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
    }

    suspend fun getPublishers(): List<Histo> {
        return if ( query.query.isNullOrEmpty() ) {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            bookDao.getFilteredPublisher(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            bookDao.getTitlePublisher(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
    }

    suspend fun getAuthors(): List<Histo> {
        return if ( query.query.isNullOrEmpty() ) {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            bookDao.getFilteredAuthor(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isPublisherEmpty = query.publisher.isNullOrEmpty()
            bookDao.getTitleAuthor(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isPublisherEmpty, if (isPublisherEmpty) "" else query.publisher!!)
        }
    }

    /**
     * Loads a book by id.
     */
    suspend fun load(bookId: Int): Book {
        return bookDao.load(bookId)
    }

    /**
     * Saves - inserts or updates - keeps track of dateAdded and last modified timestamps.
     */
    suspend fun save(book: Book) {
        val timestamp = System.currentTimeMillis() / 1000
        if (book.id <= 0) {
            if (book.rawDateAdded <= 0) {
                book.rawDateAdded = timestamp
            }
            book.lastModified = timestamp
            bookDao.insert(book)
        } else {
            book.lastModified = timestamp
            bookDao.update(book)
        }
    }


}