package com.anselm.books

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book)

    @Query("DELETE FROM book_table")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM book_table ORDER BY title, author DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllPagedList(limit: Int, offset: Int): List<Book>

    @Query("SELECT * FROM book_table " +
            "JOIN book_fts ON book_table.id = book_fts.rowid " +
            "WHERE book_fts MATCH :query " +
            "ORDER BY title, author DESC LIMIT :limit OFFSET :offset")
    suspend fun getTitlePagedList(query: String, limit: Int, offset: Int) : List<Book>
}
