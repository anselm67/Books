package com.anselm.books

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(entities = arrayOf(Book::class), version = 1, exportSchema = false)
abstract class BookRoomDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    private class BookRoomDatabaseCallback(
        private val scope: CoroutineScope
    ) : Callback() {
        override fun onCreate(database: SupportSQLiteDatabase) {
            super.onCreate(database)
            INSTANCE?.let {db -> scope.launch { populateDatabase(db.bookDao()) }
            }
        }
        suspend fun populateDatabase(bookDao: BookDao) {
            bookDao.deleteAll()

            for (i in 1..100) {
                val book = Book(0, "title $i", "author $i")
                bookDao.insert(book)
            }
        }
    }
    companion object {
        @Volatile
        private var INSTANCE: BookRoomDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): BookRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookRoomDatabase::class.java,
                    "book_database"
                ).addCallback(BookRoomDatabaseCallback(scope)).build()
                INSTANCE = instance
                instance
            }
        }

    }
}