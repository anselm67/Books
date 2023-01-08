package com.anselm.books.database

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.TAG
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BookRepository(private val dao: BookDao) {

    suspend fun getTotalCount(): Int {
        return dao.getTotalCount()
    }

    suspend fun getPagedList(query: Query, limit: Int, offset: Int): List<Book> {
        Log.d(TAG, "getPagedList [$offset, $limit] ${query.query}/${query.partial}," +
                " filters: '${query.filters}'," +
                " sort: ${query.sortBy}"
        )
        return if ( query.query.isNullOrEmpty() ) {
            dao.getFilteredPagedList(
                query.filters.map { it.labelId },
                query.sortBy, limit, offset
            )
        } else /* Requests text matching. */ {
            dao.getTitlePagedList(
                if (query.partial) query.query!! + '*' else query.query!!,
                query.filters.map { it.labelId },
                query.sortBy, limit, offset
            )
        }
    }

    suspend fun getPagedListCount(query: Query): Int {
        Log.d(TAG, "getPagedListCount ${query.query}/${query.partial}," +
                " filters: '${query.filters}',"
        )
        val count = if ( query.query.isNullOrEmpty() ) {
            dao.getFilteredPagedListCount(
                query.filters.map { it -> it.labelId },
            )
        } else /* Requests text matching. */ {
            dao.getTitlePagedListCount(
                if (query.partial) query.query!! + '*' else query.query!!,
                query.filters.map { it -> it.labelId },
            )
        }
        return count
    }

    suspend fun getIdsList(query: Query): List<Long> {
        return if ( query.query.isNullOrEmpty() ) {
            dao.getFilteredIdsList(
                query.filters.map { it -> it.labelId },
                query.sortBy,
            )
        } else /* Requests text matching. */ {
            dao.getTitleIdsList(
                if (query.partial) query.query!! + '*' else query.query!!,
                query.filters.map { it -> it.labelId },
                query.sortBy,
            )
        }
    }

    fun deleteAll() {
        clearLabelCaches()
        app.database.clearAllTables()
        Log.d(TAG, "deleteAll: cleared all tables.")
    }

    suspend fun deleteBook(book: Book) {
        dao.deleteBook(book)
    }

    suspend fun getHisto(
        type: Label.Type,
        labelQuery: String? = null,
        sortBy: Int = BookDao.SortByCount,
        query: Query = Query.emptyQuery,
    ): List<Histo> {
        val histos = if ( query.query.isNullOrEmpty() ) {
            if (labelQuery.isNullOrEmpty()) {
                dao.getFilteredHisto(
                    type.type, query.filters.map { it.labelId }, sortBy,
                )
            } else {
                dao.searchFilteredHisto(
                    type.type, labelQuery, query.filters.map { it.labelId }, sortBy,
                )
            }
        } else /* Requests text match. */ {
            if (labelQuery.isNullOrEmpty()) {
                dao.getTitleHisto(
                    type.type,
                    if (query.partial) query.query!! + '*' else query.query!!,
                    query.filters.map { it.labelId },
                    sortBy,
                )
            } else {
                dao.searchTitleHisto(
                    type.type,
                    labelQuery,
                    if (query.partial) query.query!! + '*' else query.query!!,
                    query.filters.map { it.labelId },
                    sortBy,
                )
            }
        }
        histos.forEach { it.text = label(it.labelId).name }
        return histos
    }

    /**
     * Loads a book by id.
     * If there is any chance you'll save the book down the road, you have to
     * set decorate to true, as saving the book requires access to the authors.
     */
    suspend fun load(bookId: Long, decorate: Boolean = false): Book? {
        val book = dao.load(bookId)
        if (book != null && decorate) {
            decorate(book)
        }
        return book
    }

    /**
     * Saves - inserts or updates - keeps track of dateAdded and last modified timestamps.
     */
    suspend fun save(book: Book, saveCover: Boolean = true) {
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
        if (book.id <= 0 && book.imageFilename.isEmpty() && saveCover) {
            // Fetches the image cover right away.
            // - The saveCover flag will avoid an infinite loop. For sure.
            // - We *have* - sigh - to load the book again so that book.id is set which allows us
            //   to update the book using save(Book).
            app.imageRepository.fetchCover(book) { _, filename ->
                app.applicationScope.launch {
                    load(bookId, true)?.let { book ->
                        book.imageFilename = filename
                        save(book, false)
                    }
                }
            }
        }
    }

    /**
     * Creates a new book for insertion.
     * This might set some default values for some fields based on preferences.
     */
    fun newBook(): Book {
        return Book()
    }

    /**
     * Handling of cached labels.
     * All labels are to be gotten through these methods which caches them as needed.
     */
    private val labelsByValue = HashMap<Pair<Label.Type,String>, Label>()
    private val labelsById = HashMap<Long, Label>()

    private fun clearLabelCaches() {
        labelsByValue.clear()
        labelsById.clear()
    }

    suspend fun label(type: Label.Type, rawName: String): Label {
        val name = rawName.trim()
        val key = Pair(type, name)
        var label = labelsByValue[key]
        if (label == null) {
            // No need for synchronization as the underlying sql table has a unique constraint.
            label = dao.label(type.type, name)
            if (label == null) {
                val id = dao.insert(Label(type, name))
                label = Label(id, type, name)
            }
            labelsByValue[key] = label
            labelsById[label.id] = label
        }
        return label
    }

    // A (B)locking version of label, cause it's used everywhere and usually right in the cache.
    fun labelB(type: Label.Type, name: String): Label {
        var label: Label
        runBlocking {
            label = label(type, name)
        }
        return label
    }

    fun labelOrNullB(type: Label.Type, name: String):Label? {
        return if (name.isEmpty()) null else labelB(type, name)

    }

    suspend fun label(id: Long): Label {
        var label = labelsById[id]
        if (label == null) {
            label = dao.label(id)
            check(label != null)
            labelsByValue[Pair(label.type, label.name)] = label
            labelsById[label.id] = label
        }
        return label
    }

    suspend fun decorate(book: Book) {
        book.decorate(dao.labels(book.id).map { label(it) })
    }
}