package com.anselm.books.database

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.anselm.books.Book
import com.anselm.books.BookPagingSource
import com.anselm.books.TAG

class BookRepository(private val dao: BookDao) {
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
        return dao.getTotalCount()
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
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getFilteredPagedList(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!,
                query.sortBy, limit, offset)
        } else /* Requests text matching. */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getTitlePagedList(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
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
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getFilteredPagedListCount(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* Requests text matching. */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getTitlePagedListCount(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
        itemCount.postValue(count)
        return count
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun getLocations(): List<Histo> {
        return if ( query.query.isNullOrEmpty() ) {
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getFilteredPhysicalLocation(
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* Requests text match. */ {
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getTitlePhysicalLocation(
                if (query.partial) query.query!! + '*' else query.query!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
    }

    suspend fun getGenres(): List<Histo> {
        return if ( query.query.isNullOrEmpty() ) {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getFilteredGenre(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getTitleGenre(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
    }

    suspend fun getPublishers(): List<Histo> {
        val histos = if ( query.query.isNullOrEmpty() ) {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getFilteredPublisher(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            val isAuthorEmpty = query.author.isNullOrEmpty()
            dao.getTitlePublisher(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!,
                isAuthorEmpty, if (isAuthorEmpty) "" else query.author!!)
        }
        // Convert the string-encoded labelId into it's value.
        // Oh the excitement!!!
        for (h in histos) {
            h.text = label(h.text.toLong()).name
        }
        return histos
    }

    suspend fun getAuthors(): List<Histo> {
        return if ( query.query.isNullOrEmpty() ) {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            dao.getFilteredAuthor(
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!)
        } else /* titleQuery is not empty */ {
            val isLocationEmpty = query.location.isNullOrEmpty()
            val isGenreEmpty = query.genre.isNullOrEmpty()
            dao.getTitleAuthor(
                if (query.partial) query.query!! + '*' else query.query!!,
                isLocationEmpty, if (isLocationEmpty) "" else query.location!!,
                isGenreEmpty, if (isGenreEmpty) "" else query.genre!!)
        }
    }

    /**
     * Loads a book by id.
     */
    suspend fun load(bookId: Long, decorate: Boolean = false): Book {
        val book = dao.load(bookId)
        if ( decorate ) {
            decorate(book)
        }
        return book
    }

    /**
     * Saves - inserts or updates - keeps track of dateAdded and last modified timestamps.
     */
    suspend fun save(book: Book) {
        var bookId = book.id
        val timestamp = System.currentTimeMillis() / 1000
        if (book.id <= 0) {
            if (book.rawDateAdded <= 0) {
                book.rawDateAdded = timestamp
            }
            bookId = dao.insert(book)
        } else {
            book.rawLastModified = timestamp
            dao.update(book)
        }
        if (book.labelsChanged) {
            dao.clearLabels(book.id)
            var sortKey = 0
            dao.insert(*book.labels!!.map {
                // Saves the label to the database if needed, by going through the cache.
                val label = label(it.type, it.name)
                BookLabels(bookId, label.id, sortKey++)
            }.toTypedArray())
        }
    }

    /**
     * Handling of cached labels.
     * All labels are to be gotten through these methods which caches them as needed.
     */
    private val labelsByValue = HashMap<Pair<Int,String>, Label>()
    private val labelsById = HashMap<Long, Label>()

    suspend fun label(type: Int, name: String): Label {
        val key = Pair(type, name)
        var label = labelsByValue[key]
        if (label == null) {
            // No need for synchronization as the underlying sql table has a unique constraint.
            label = dao.label(type, name)
            if (label == null) {
                val id = dao.insert(Label(type, name))
                label = Label(id, type, name)
            }
            labelsByValue[key] = label
            labelsById[label.id] = label
        }
        return label
    }

    suspend fun label(id: Long): Label {
        var label = labelsById[id]
        if (label == null) {
            label = dao.label(id)
            labelsByValue[Pair(label.type, label.name)] = label
            labelsById[label.id] = label
        }
        return label
    }

    private suspend fun decorate(book: Book) {
        book.decorate(dao.labels(book.id).map { label(it) })
    }


}