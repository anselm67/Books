package com.anselm.books

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val NUMBER_OF_FAKE_ITEMS = 3000

@Database(entities = arrayOf(Book::class), version = 1, exportSchema = false)
abstract class BookRoomDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    private class BookDatabaseCallback(
        private val scope: CoroutineScope
    ) : Callback() {
        override fun onCreate(database: SupportSQLiteDatabase) {
            super.onCreate(database)
            INSTANCE?.let { db -> scope.launch { populateDatabase(db.bookDao()) } }
        }

        suspend fun populateDatabase(bookDao: BookDao) {
            bookDao.deleteAll()

            for (i in 1.. NUMBER_OF_FAKE_ITEMS) {
                val book = Book("title $i", "author $i")
                bookDao.insert(book)
            }
            Log.d(TAG, "Created $NUMBER_OF_FAKE_ITEMS fake books.")
        }
    }
    companion object {
        @Volatile
        private var INSTANCE: BookRoomDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): BookRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    BookRoomDatabase::class.java,
                    "book_database"
                ).addCallback(BookDatabaseCallback(scope))
                .build()
                .also { INSTANCE = it }
            }
        }

    }
}