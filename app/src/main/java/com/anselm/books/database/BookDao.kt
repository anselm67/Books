package com.anselm.books.database

import androidx.room.*
import androidx.room.Query

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
    suspend fun deleteBooks(): Int

    @Query("DELETE FROM label_table")
    suspend fun deleteLabels(): Int

    @Query("DELETE FROM book_labels")
    suspend fun deleteBookLabels(): Int

    @Transaction
    suspend fun deleteAll():Int {
        return deleteBooks() + deleteLabels() + deleteBookLabels()
    }

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
            "   AND id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            ") " +
            " ORDER BY " +
            "   CASE WHEN :param = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :param = 2 THEN date_added END DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getTitlePagedList(
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int, limit: Int, offset: Int
    ): List<Book>

    @Query("SELECT COUNT(*) FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            "   AND id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            ")")
    suspend fun getTitlePagedListCount(
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long
    ): Int

    @Query("SELECT * FROM book_table " +
            " WHERE id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            ") " +
            " ORDER BY " +
            "   CASE WHEN :param = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :param = 2 THEN date_added END DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getFilteredPagedList(
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int, limit: Int, offset: Int
    ): List<Book>

    @Query("SELECT COUNT(*) FROM book_table " +
            " WHERE id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            ")")
    suspend fun getFilteredPagedListCount(
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long
    ): Int

    /**
     * Two queries for histograms.
     */
    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_fts ON book_table.id = book_fts.rowid," +
            "       book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId " +
            " WHERE " +
            "    book_fts MATCH :query" +
            "    AND label_table.type = :type" +
            "    AND book_table.id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            ") " +
            " GROUP BY labelId ORDER BY count DESC")
    suspend fun getTitleHisto(
        type: Int,
        query: String,
        labelId1: Long, labelId2: Long,labelId3: Long,labelId4: Long
    ): List<Histo>

    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId " +
            " WHERE label_table.type = :type" +
            "    AND label_table.type = :type" +
            "    AND book_table.id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            ") " +
            " GROUP BY labelId ORDER BY count DESC")
    suspend fun getFilteredHisto(
        type: Int, labelId1: Long, labelId2: Long,labelId3: Long,labelId4: Long
    ): List<Histo>

    companion object {
        const val SortByTitle = 1
        const val SortByDateAdded = 2
    }
}

data class Histo(val labelId: Long, val count: Int, var text: String? = null)

