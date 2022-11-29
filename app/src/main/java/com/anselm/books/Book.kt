package com.anselm.books

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity(
    tableName = "book_table",
    indices = [Index(value = ["title", "author"] )]
)
data class Book(@PrimaryKey(autoGenerate=true) val id: Int = 0) {
    @ColumnInfo(name = "title")
    var title = ""

    @ColumnInfo(name = "author")
    var author = ""

    constructor(title: String, author: String) : this() {
        this.title = title
        this.author = author
    }

    constructor(o: JSONObject) : this() {
        this.title = o.getString("title")
        this.author = o.getString("author")
    }
}
