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
    suspend fun load(bookId: Long) : Book?

    @Query("SELECT bt.* FROM book_table AS bt " +
            " LEFT JOIN book_labels AS lb ON lb.bookId = bt.id " +
            " WHERE bt.title = :title " +
            "   AND (:authorId = 0) OR (lb.labelId = :authorId)")
    suspend fun getDuplicates(title: String, authorId: Long): List<Book>

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Transaction
    suspend fun deleteBook(book: Book) {
        clearLabels(book.id)
        delete(book)
    }

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
    suspend fun label(id: Long): Label?

    @Query("SELECT labelId from book_labels WHERE bookId = :bookId ORDER BY sortKey ASC")
    suspend fun labels(bookId: Long): List<Long>

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
            "   AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "        OR id IN (" +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId1 = 0 OR labelId = :labelId1" +
            "    INTERSECT " +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId2 = 0 OR labelId = :labelId2" +
            "    INTERSECT " +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId3 = 0 OR labelId = :labelId3" +
            "    INTERSECT " +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId4 = 0 OR labelId = :labelId4" +
            "   )) " +
            " ORDER BY " +
            "   CASE WHEN :param = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :param = 2 THEN date_added END DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getTitlePagedList(
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int, limit: Int, offset: Int
    ): List<Book>

    suspend fun getTitlePagedList(
        query: String, labelIds: List<Long>, param: Int, limit: Int, offset: Int
    ): List<Book> {
        when (labelIds.size) {
            0 -> return getTitlePagedList(
                query, 0L, 0L, 0L, 0L, param, limit, offset
            )
            1 -> return getTitlePagedList(
                query, labelIds[0], 0L, 0L, 0L, param, limit, offset
            )
            2 -> return getTitlePagedList(
                query, labelIds[0], labelIds[1], 0L, 0L, param, limit, offset
            )
            3 -> return getTitlePagedList(
                query, labelIds[0], labelIds[1], labelIds[2], 0L, param, limit, offset
            )
            4 -> return getTitlePagedList(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], param, limit, offset
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT COUNT(*) FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            "   AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "        OR id IN (" +
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
            "))")
    suspend fun getTitlePagedListCount(
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
    ): Int

    suspend fun getTitlePagedListCount(
        query: String, labelIds: List<Long>,
    ): Int {
        when (labelIds.size) {
            0 -> return getTitlePagedListCount(
                query, 0L, 0L, 0L, 0L,
            )
            1 -> return getTitlePagedListCount(
                query, labelIds[0], 0L, 0L, 0L,
            )
            2 -> return getTitlePagedListCount(
                query, labelIds[0], labelIds[1], 0L, 0L,
            )
            3 -> return getTitlePagedListCount(
                query, labelIds[0], labelIds[1], labelIds[2], 0L,
            )
            4 -> return getTitlePagedListCount(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3],
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return 0
    }


    @Query("SELECT * FROM book_table " +
            " WHERE (:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "    OR id IN (" +
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
            ")" +
            " ORDER BY " +
            "   CASE WHEN :param = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :param = 2 THEN date_added END DESC " +
            "LIMIT :limit OFFSET :offset")
    suspend fun getFilteredPagedList(
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int, limit: Int, offset: Int
    ): List<Book>

    suspend fun getFilteredPagedList(
        labelIds: List<Long>, param: Int, limit: Int, offset: Int
    ): List<Book> {
        when (labelIds.size) {
            0 -> return getFilteredPagedList(
                0L, 0L, 0L, 0L, param, limit, offset
            )
            1 -> return getFilteredPagedList(
                labelIds[0], 0L, 0L, 0L, param, limit, offset
            )
            2 -> return getFilteredPagedList(
                labelIds[0], labelIds[1], 0L, 0L, param, limit, offset
            )
            3 -> return getFilteredPagedList(
                labelIds[0], labelIds[1], labelIds[2], 0L, param, limit, offset
            )
            4 -> return getFilteredPagedList(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3], param, limit, offset
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT COUNT(*) FROM book_table " +
            " WHERE (:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "    OR id IN (" +
            "SELECT bookId FROM book_labels " +
            "    WHERE :labelId1 = 0 OR labelId = :labelId1" +
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

    suspend fun getFilteredPagedListCount(
        labelIds: List<Long>,
    ): Int {
        when (labelIds.size) {
            0 -> return getFilteredPagedListCount(
                0L, 0L, 0L, 0L,
            )
            1 -> return getFilteredPagedListCount(
                labelIds[0], 0L, 0L, 0L,
            )
            2 -> return getFilteredPagedListCount(
                labelIds[0], labelIds[1], 0L, 0L,
            )
            3 -> return getFilteredPagedListCount(
                labelIds[0], labelIds[1], labelIds[2], 0L,
            )
            4 -> return getFilteredPagedListCount(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3],
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return 0
    }

    /**
     * Queries for retrieving IDs of books in view.
     */
    @Query("SELECT book_table.id FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            "   AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "        OR id IN (" +
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
            "))" +
            " ORDER BY " +
            "   CASE WHEN :param = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :param = 2 THEN date_added END DESC ")
    suspend fun getTitleIdsList(
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int,
    ): List<Long>

    suspend fun getTitleIdsList(
        query: String, labelIds: List<Long>, param: Int,
    ): List<Long> {
        when (labelIds.size) {
            0 -> return getTitleIdsList(
                query, 0L, 0L, 0L, 0L, param,
            )
            1 -> return getTitleIdsList(
                query, labelIds[0], 0L, 0L, 0L, param,
            )
            2 -> return getTitleIdsList(
                query, labelIds[0], labelIds[1], 0L, 0L, param,
            )
            3 -> return getTitleIdsList(
                query, labelIds[0], labelIds[1], labelIds[2], 0L, param,
            )
            4 -> return getTitleIdsList(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], param,
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT book_table.id FROM book_table " +
            " WHERE (:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "    OR id IN (SELECT bookId FROM book_labels " +
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
            "   CASE WHEN :param = 2 THEN date_added END DESC ")
    suspend fun getFilteredIdsList(
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int,
    ): List<Long>

    suspend fun getFilteredIdsList(
        labelIds: List<Long>, param: Int,
    ): List<Long> {
        when (labelIds.size) {
            0 -> return getFilteredIdsList(
                0L, 0L, 0L, 0L, param,
            )
            1 -> return getFilteredIdsList(
                labelIds[0], 0L, 0L, 0L, param,
            )
            2 -> return getFilteredIdsList(
                labelIds[0], labelIds[1], 0L, 0L, param,
            )
            3 -> return getFilteredIdsList(
                labelIds[0], labelIds[1], labelIds[2], 0L, param,
            )
            4 -> return getFilteredIdsList(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3], param,
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    /**
     * Histogram queries: get{Title,Filtered} Histo and search{Title,Filtered}Histo.
     */
    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_fts ON book_table.id = book_fts.rowid," +
            "       book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId " +
            " WHERE " +
            "    book_fts MATCH :query" +
            "    AND label_table.type = :type " +
            "    AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) "+
            "         OR book_table.id IN (" +
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
            "))" +
            " GROUP BY labelId " +
            " ORDER BY CASE WHEN :param = 3 THEN count END DESC, " +
            "          CASE WHEN :param = 1 THEN label_table.name END ASC")
    suspend fun getTitleHisto(
        type: Int,
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int,
    ): List<Histo>

    suspend fun getTitleHisto(
        type: Int, query: String, labelIds: List<Long>,
        param: Int = SortByCount,
    ): List<Histo> {
        when (labelIds.size) {
            0 -> return getTitleHisto(
                type, query, 0L, 0L, 0L, 0L, param,
            )
            1 -> return getTitleHisto(
                type, query, labelIds[0], 0L, 0L, 0L, param,
            )
            2 -> return getTitleHisto(
                type, query, labelIds[0], labelIds[1], 0L, 0L, param,
            )
            3 -> return getTitleHisto(
                type, query, labelIds[0], labelIds[1], labelIds[2], 0L, param,
            )
            4 -> return getTitleHisto(
                type, query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], param,
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_fts ON book_table.id = book_fts.rowid," +
            "       book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId, " +
            "       label_fts ON label_table.id = label_fts.rowid " +
            " WHERE " +
            "    book_fts MATCH :query " +
            "    AND label_fts MATCH :labelQuery" +
            "    AND label_table.type = :type" +
            "    AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) "+
            "         OR book_table.id IN (" +
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
            "))" +
            " GROUP BY labelId "+
            " ORDER BY CASE WHEN :param = 3 THEN count END DESC, " +
            "          CASE WHEN :param = 1 THEN label_table.name END ASC")
    suspend fun searchTitleHisto(
        type: Int,
        labelQuery: String, query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int,
    ): List<Histo>

    suspend fun searchTitleHisto(
        type: Int,
        labelQuery: String, query: String,
        labelIds: List<Long>,
        param: Int = SortByCount
    ): List<Histo> {
        when (labelIds.size) {
            0 -> return searchTitleHisto(
                type, labelQuery, query, 0L, 0L, 0L, 0L, param,
            )
            1 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], 0L, 0L, 0L, param,
            )
            2 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], labelIds[1], 0L, 0L, param,
            )
            3 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], labelIds[1], labelIds[2], 0L, param,
            )
            4 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], param,
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId " +
            " WHERE label_table.type = :type" +
            "    AND label_table.type = :type" +
            "    AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) "+
            "         OR book_table.id IN (" +
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
            "))" +
            " GROUP BY labelId " +
            " ORDER BY CASE WHEN :param = 3 THEN count END DESC, " +
            "          CASE WHEN :param = 1 THEN label_table.name END ASC")

    suspend fun getFilteredHisto(
        type: Int,
        labelId1: Long, labelId2: Long,labelId3: Long,labelId4: Long,
        param: Int,
    ): List<Histo>

    suspend fun getFilteredHisto(
        type: Int, labelIds: List<Long>, param: Int = SortByCount
    ): List<Histo> {
        when (labelIds.size) {
            0 -> return getFilteredHisto(
                type, 0L, 0L, 0L, 0L, param,
            )
            1 -> return getFilteredHisto(
                type, labelIds[0], 0L, 0L, 0L, param,
            )
            2 -> return getFilteredHisto(
                type, labelIds[0], labelIds[1], 0L, 0L, param,
            )
            3 -> return getFilteredHisto(
                type, labelIds[0], labelIds[1], labelIds[2], 0L, param,
            )
            4 -> return getFilteredHisto(
                type, labelIds[0], labelIds[1], labelIds[2], labelIds[3], param,
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId, " +
            "       label_fts ON label_table.id = label_fts.rowid " +
            " WHERE label_fts MATCH :labelQuery" +
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
            " GROUP BY labelId " +
            " ORDER BY CASE WHEN :param = 3 THEN count END DESC, " +
            "          CASE WHEN :param = 1 THEN label_table.name END ASC")
    suspend fun searchFilteredHisto(
        type: Int,
        labelQuery: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,
        param: Int,
    ): List<Histo>

    suspend fun searchFilteredHisto(
        type: Int,
        labelQuery: String,
        labelIds: List<Long>,
        param: Int = SortByCount,
    ): List<Histo> {
        when (labelIds.size) {
            0 -> return searchFilteredHisto(
                type, labelQuery, 0L, 0L, 0L, 0L, param,
            )
            1 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], 0L, 0L, 0L, param,
            )
            2 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], labelIds[1], 0L, 0L, param,
            )
            3 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], labelIds[1], labelIds[2], 0L, param,
            )
            4 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], labelIds[1], labelIds[2], labelIds[3], param,
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    /**
     * Stats queries.
     */
    @Query("SELECT type, COUNT(*) as count FROM label_table GROUP BY type")
    @TypeConverters(Converters::class)
    suspend fun getLabelTypeCounts(): List<LabelTypeCount>

    @Query("     SELECT bt1.id FROM book_table AS bt1 " +
            " LEFT JOIN book_labels AS bl1 ON bl1.bookId = bt1.id " +
            "     WHERE (title, labelId) IN " +
            "     (SELECT b.title AS title, lt.id AS labelId FROM book_table AS b" +
            "   LEFT JOIN book_labels AS bl ON bl.bookId = b.id " +
            "        JOIN label_table as lt on lt.id = bl.labelId " +
            "       WHERE lt.type = 1 " +
            "    GROUP BY title, name HAVING count(*) > 1)")
    suspend fun getDuplicateBooksIds(): List<Long>

    @Query("SELECT COUNT(*) FROM (" +
            "    SELECT b.title as title, lt.name as name , count(*) as count " +
            "      FROM book_table as b " +
            " LEFT JOIN book_labels as bl on bl.bookId = b.id " +
            "      JOIN label_table as lt on lt.id = bl.labelId " +
            "     WHERE lt.type = 1 " + // Label.Typ.Authors = 1
            "GROUP BY title, name HAVING count > 1" +
            ")")
    suspend fun getDuplicateBooksCount(): Int

    @Query("SELECT COUNT(*) FROM book_table " +
            " WHERE id NOT IN (" +
            "    SELECT bl.bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "      WHERE lt.type = :type" +
            ")")
    @TypeConverters(Converters::class)
    suspend fun getBooksWithoutLabelCount(type: Label.Type): Int

    @Query(" SELECT COUNT(*) FROM book_table " +
            " WHERE image_filename = '' OR image_filename IS NULL")
    suspend fun getWithoutCoverBooksCount(): Int

    @Query(" SELECT id FROM book_table " +
            " WHERE image_filename = '' OR image_filename IS NULL")
    suspend fun getWithoutCoverBooksIds(): List<Long>

    @Query("DELETE FROM label_table " +
           " WHERE id NOT IN (SELECT labelId FROM book_labels)")
    suspend fun deleteUnusedLabels(): Int

    companion object {
        const val SortByTitle = 1
        const val SortByDateAdded = 2
        const val SortByCount = 3
    }
}

data class LabelTypeCount(
    val type: Label.Type,
    val count: Int,
)

data class Histo(
    val labelId: Long,
    val count: Int)
{
    @Ignore
    var text: String? = null
}

