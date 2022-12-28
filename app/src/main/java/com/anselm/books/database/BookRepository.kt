package com.anselm.books.database

import android.util.Log
import androidx.lifecycle.MutableLiveData
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
            dao.getFilteredPagedList(
                query.author, query.genre, query.publisher, query.location,
                query.sortBy, limit, offset
            )
        } else /* Requests text matching. */ {
            dao.getTitlePagedList(
                if (query.partial) query.query!! + '*' else query.query!!,
                query.author, query.genre, query.publisher, query.location,
                query.sortBy, limit, offset
            )
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
            dao.getFilteredPagedListCount(
                query.author, query.genre, query.publisher, query.location,
            )
        } else /* Requests text matching. */ {
            dao.getTitlePagedListCount(
                if (query.partial) query.query!! + '*' else query.query!!,
                query.author, query.genre, query.publisher, query.location,
            )
        }
        itemCount.postValue(count)
        return count
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun getHisto(type: Int, labelQuery: String?): List<Histo> {
        val histos = if ( query.query.isNullOrEmpty() ) {
            if (labelQuery.isNullOrEmpty()) {
                dao.getFilteredHisto(
                    type, query.genre, query.author, query.location, query.publisher
                )
            } else {
                dao.searchFilteredHisto(
                    type, labelQuery, query.genre, query.author, query.location, query.publisher
                )
            }
        } else /* Requests text match. */ {
            if (labelQuery.isNullOrEmpty()) {
                dao.getTitleHisto(
                    type,
                    if (query.partial) query.query!! + '*' else query.query!!,
                    query.genre, query.author, query.location, query.publisher
                )
            } else {
                dao.searchTitleHisto(
                    type,
                    labelQuery,
                    if (query.partial) query.query!! + '*' else query.query!!,
                    query.genre, query.author, query.location, query.publisher
                )
            }
        }
        histos.forEach { it.text = label(it.labelId).name }
        return histos
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

    suspend fun decorate(book: Book) {
        book.decorate(dao.labels(book.id).map { label(it) })
    }


}