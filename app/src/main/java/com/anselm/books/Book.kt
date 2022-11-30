package com.anselm.books

import androidx.room.*
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

    @ColumnInfo(name = "imgUrl")
    var imgUrl = ""

    constructor(title: String, author: String, imgUrl: String = "") : this() {
        this.title = title
        this.author = author
        this.imgUrl = imgUrl
    }

    constructor(o: JSONObject) : this() {
        this.title = o.getString("title")
        this.author = o.getString("author")
        this.imgUrl = o.getString("uploaded_image_url")
    }
}

@Entity(tableName = "book_fts")
@Fts4(
    contentEntity = Book::class
)
data class BookFTS(
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "author")
    val author: String
)
