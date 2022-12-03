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

    @ColumnInfo(name = "physicalLocation")
    var physicalLocation = ""

    @ColumnInfo(name = "ISBN")
    var isbn = ""

    @ColumnInfo(name = "summary")
    var summary = ""

    @ColumnInfo(name = "yearPublished")
    var yearPublished = ""

    @ColumnInfo(name = "numberOfPages")
    var numberOfPages = ""

    constructor(title: String, author: String, imgUrl: String = "") : this() {
        this.title = title
        this.author = author
        this.imgUrl = imgUrl
    }

    constructor(o: JSONObject) : this() {
        this.title = o.getString("title")
        this.author = o.getString("author")
        this.imgUrl = o.getString("uploaded_image_url")
        this.physicalLocation = o.getString("physical_location")
        this.isbn = o.getString("isbn")
        this.summary = o.getString("summary")
        this.yearPublished = o.getString("year_published")
        this.numberOfPages = o.getString("number_of_pages")
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
