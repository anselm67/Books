package com.anselm.books.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anselm.books.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [
        Book::class, BookFTS::class,
        Label::class, LabelFTS::class,
        BookLabels::class
    ],
    version = 19,
    exportSchema = false)
abstract class BookDatabase : RoomDatabase() {

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
            Log.d(TAG, "Cleared database through deleteAll.")
        }
    }
    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): BookDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "book_database"
                ).fallbackToDestructiveMigration()
                .addCallback(BookDatabaseCallback(scope))
                .build()
                .also { INSTANCE = it }
            }
        }

    }
}