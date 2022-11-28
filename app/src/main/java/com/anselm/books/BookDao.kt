package com.anselm.books

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book)

    @Query("DELETE FROM book_table")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM book_table ORDER BY id ASC")
    fun getAllBooks(): Flow<List<Book>>
}
