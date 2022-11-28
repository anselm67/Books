package com.anselm.books

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_table")
data class Book(@PrimaryKey(autoGenerate=true) val id: Int = 0,
                val title: String,
                val author: String)
