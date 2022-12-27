package com.anselm.books.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anselm.books.Book

@Dao
interface BookDao {
    /**
     * Handling Book: insert, load and update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Query("SELECT * FROM book_table WHERE id = :bookId")
    suspend fun load(bookId: Long) : Book

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    /**
     * Handling of labels.
     * This section covers lookup by id and value, insertion and fetching.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: Label): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg bookLabels: BookLabels)

    @Query("SELECT * FROM label_table WHERE type = :type AND name = :name")
    suspend fun label(type: Int, name: String): Label?

    @Query("SELECT * FROM label_table WHERE id = :id")
    suspend fun label(id: Long): Label

    @Query("SELECT labelId from book_labels WHERE bookId = :bookId ORDER BY sortKey ASC")
    suspend fun labels(bookId: Long): LongArray

    @Query("DELETE FROM book_labels WHERE bookId = :bookId")
    suspend fun clearLabels(bookId: Long)

    /**
     * Clears everything.
     */
    @Query("DELETE FROM book_table")
    suspend fun deleteAll(): Int

    /**
     * Gets the total number of books in the library.
     */
    @Query("SELECT COUNT(*) FROM book_table")
    suspend fun getTotalCount(): Int

    /**
     * Two queries for top level search.
     */
    @Query("SELECT * FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            "   AND (:locationCond OR physicalLocation = :physicalLocation)" +
            "   AND (:genreCond OR genre = :genre)" +
            "   AND (:authorCond OR book_table.author = :author)" +
            " ORDER BY " +
            "   CASE WHEN :param = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :param = 2 THEN date_added END DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getTitlePagedList(
        query: String,
        locationCond: Boolean, physicalLocation: String,
        genreCond: Boolean, genre: String,
        authorCond: Boolean, author: String,
        param: Int, limit: Int, offset: Int) : List<Book>

    @Query("SELECT COUNT(*) FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            "   AND (:locationCond OR physicalLocation = :physicalLocation)" +
            "   AND (:genreCond OR genre = :genre)" +
            "   AND (:authorCond OR book_table.author = :author)")
    suspend fun getTitlePagedListCount(
        query: String,
        locationCond: Boolean, physicalLocation: String,
        genreCond: Boolean, genre: String,
        authorCond: Boolean, author: String) : Int

    @Query("SELECT * FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE (:locationCond OR physicalLocation = :physicalLocation)" +
            "   AND (:genreCond OR genre = :genre)" +
            "   AND (:authorCond OR book_table.author = :author)" +
            " ORDER BY " +
            "   CASE WHEN :param = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :param = 2 THEN date_added END DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getFilteredPagedList(
        locationCond: Boolean, physicalLocation: String,
        genreCond: Boolean, genre: String,
        authorCond: Boolean, author: String,
        param: Int, limit: Int, offset: Int) : List<Book>

    @Query("SELECT COUNT(*) FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE (:locationCond OR physicalLocation = :physicalLocation)" +
            "   AND (:genreCond OR genre = :genre)" +
            "   AND (:authorCond OR book_table.author = :author)")
    suspend fun getFilteredPagedListCount(
        locationCond: Boolean, physicalLocation: String,
        genreCond: Boolean, genre: String,
        authorCond: Boolean, author: String) : Int


    /**
     * Two queries for (physical) location histograms.
     */
    @Query("SELECT physicalLocation as text, count(*) as count FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            "   AND text != \"\"" +
            "   AND (:genreCond OR genre = :genre)" +
            "   AND (:authorCond OR book_table.author = :author)" +
            "  GROUP by physicalLocation ORDER by count DESC")
    suspend fun getTitlePhysicalLocation(
        query: String,
        genreCond: Boolean, genre: String,
        authorCond: Boolean, author: String): List<Histo>


    @Query("SELECT physicalLocation as text, count(*) as count FROM book_table " +
            " WHERE text != \"\" " +
            "   AND (:genreCond OR genre = :genre)" +
            "   AND (:authorCond OR book_table.author = :author)" +
            " GROUP by physicalLocation ORDER by count DESC")
    suspend fun getFilteredPhysicalLocation(
        genreCond: Boolean, genre: String,
        authorCond: Boolean, author: String
    ): List<Histo>

    /**
     * Two queries for genre histograms.
     */
    @Query("SELECT genre as text, count(*) as count FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            "   AND text != \"\"" +
            "   AND (:locationCond OR physicalLocation= :location)" +
            "   AND (:authorCond OR book_table.author = :author)" +
            "  GROUP by genre ORDER by count DESC")
    suspend fun getTitleGenre(
        query: String,
        locationCond: Boolean, location: String,
        authorCond: Boolean, author: String): List<Histo>


    @Query("SELECT genre as text, count(*) as count FROM book_table " +
            " WHERE text != \"\" " +
            "   AND (:locationCond OR physicalLocation= :location)" +
            "   AND (:authorCond OR book_table.author = :author)" +
            " GROUP by genre ORDER by count DESC")
    suspend fun getFilteredGenre(
        locationCond: Boolean, location: String,
        authorCond: Boolean, author: String): List<Histo>

    /**
     * Two queries for publisher histograms.
     */
    @Query("SELECT CAST(book_labels.labelId as text) as text, COUNT(*) as count FROM book_table " +
            "  JOIN book_fts ON book_table.id = book_fts.rowid," +
            "       book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId " +
            " WHERE " +
            "    book_fts MATCH :query" +
            "    AND label_table.type = 4" +
            "    AND (:locationCond OR physicalLocation= :location)" +
            "    AND (:genreCond OR genre = :genre)" +
            "    AND (:authorCond OR book_table.author = :author)" +
            " GROUP BY labelId ORDER BY count DESC")
    suspend fun getTitlePublisher(
        query: String,
        locationCond: Boolean, location: String,
        genreCond: Boolean, genre: String,
        authorCond: Boolean, author: String): List<Histo>

    @Query("SELECT CAST(book_labels.labelId as text) as text, COUNT(*) as count FROM book_table " +
            "  JOIN book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId " +
            " WHERE label_table.type = 4" +
            "    AND (:locationCond OR physicalLocation= :location)" +
            "    AND (:genreCond OR genre = :genre)" +
            "    AND (:authorCond OR book_table.author = :author)" +
            " GROUP BY text ORDER BY count DESC")
    suspend fun getFilteredPublisher(
        locationCond: Boolean, location: String,
        genreCond: Boolean, genre: String,
        authorCond: Boolean, author: String): List<Histo>

    /**
     * Two queries for author histograms.
     */
    @Query("SELECT book_table.author as text, count(*) as count FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            "   AND text != \"\"" +
            "   AND (:locationCond OR physicalLocation= :location)" +
            "   AND (:genreCond OR genre = :genre)" +
            "  GROUP by text ORDER by count DESC")
    suspend fun getTitleAuthor(
        query: String,
        locationCond: Boolean, location: String,
        genreCond: Boolean, genre: String): List<Histo>


    @Query("SELECT author as text, count(*) as count FROM book_table " +
            " WHERE text != \"\" " +
            "   AND (:locationCond OR physicalLocation= :location)" +
            "   AND (:genreCond OR genre = :genre)" +
            " GROUP by author ORDER by count DESC")
    suspend fun getFilteredAuthor(
        locationCond: Boolean, location: String,
        genreCond: Boolean, genre: String): List<Histo>

    companion object {
        const val SortByTitle = 1
        const val SortByDateAdded = 2
    }
}

data class Histo(var /* FIXME */ text: String, val count: Int)

