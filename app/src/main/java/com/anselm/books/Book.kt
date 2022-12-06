package com.anselm.books

import androidx.room.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object BookFields {
    const val TITLE = "title"
    const val SUBTITLE = "subtitle"
    const val AUTHOR = "author"
    const val PUBLISHER = "publisher"
    const val UPLOADED_IMAGE_URL = "uploaded_image_url"
    const val PHYSICAL_LOCATION = "physical_location"
    const val ISBN = "isbn"
    const val SUMMARY = "summary"
    const val YEAR_PUBLISHED = "year_published"
    const val NUMBER_OF_PAGES = "number_of_pages"
    const val GENRE = "genre"
    const val LANGUAGE = "language"
    const val DATE_ADDED = "date_added"
}

private val DATE_FORMAT = SimpleDateFormat("EEE, MMM d yyy - hh:mm aaa", Locale.US)

@Entity(
    tableName = "book_table",
    indices = [Index(value = ["title", "author"] )]
)
data class Book(@PrimaryKey(autoGenerate=true) val id: Int = 0) {
    @ColumnInfo(name = "title")
    var title = ""

    @ColumnInfo(name = "subtitle")
    var subtitle = ""

    @ColumnInfo(name = "author")
    var author = ""

    @ColumnInfo(name = "publisher")
    var publisher = ""

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

    @ColumnInfo(name = "genre")
    var genre  = ""

    @ColumnInfo(name = "language")
    var language = ""

    @ColumnInfo(name = "date_added")
    var dateAdded = ""
        get() = if (field == "") "" else DATE_FORMAT.format(Date(field.toLong() * 1000))

    constructor(title: String, author: String, imgUrl: String = "") : this() {
        this.title = title
        this.author = author
        this.imgUrl = imgUrl
    }

    constructor(o: JSONObject) : this() {
        this.title = o.optString(BookFields.TITLE, "")
        this.subtitle = o.optString(BookFields.SUBTITLE, "")
        this.author = o.optString(BookFields.AUTHOR, "")
        this.publisher = o.optString(BookFields.PUBLISHER, "")
        this.imgUrl = o.optString(BookFields.UPLOADED_IMAGE_URL, "")
        this.physicalLocation = o.optString(BookFields.PHYSICAL_LOCATION, "")
        this.isbn = o.optString(BookFields.ISBN, "")
        this.summary = o.optString(BookFields.SUMMARY, "")
        this.yearPublished = o.optString(BookFields.YEAR_PUBLISHED, "")
        this.numberOfPages = o.optString(BookFields.NUMBER_OF_PAGES, "")
        this.genre = o.optString(BookFields.GENRE, "")
        this.language = o.optString(BookFields.LANGUAGE, "")
        this.dateAdded = o.optString(BookFields.DATE_ADDED, "")
    }

    fun get(key: String): String {
        return when(key) {
            BookFields.TITLE -> title
            BookFields.SUBTITLE -> subtitle
            BookFields.AUTHOR -> author
            BookFields.PUBLISHER -> publisher
            BookFields.UPLOADED_IMAGE_URL -> imgUrl
            BookFields.PHYSICAL_LOCATION -> physicalLocation
            BookFields.ISBN -> isbn
            BookFields.SUMMARY -> summary
            BookFields.YEAR_PUBLISHED -> yearPublished
            BookFields.NUMBER_OF_PAGES -> numberOfPages
            BookFields.GENRE -> genre
            BookFields.LANGUAGE -> language
            BookFields.DATE_ADDED -> dateAdded
            else -> "UNKNOWN KEY $key"
        }
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
