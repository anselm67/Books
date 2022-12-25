package com.anselm.books

import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import org.json.JSONObject
import org.json.JSONTokener
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
    const val IMAGE_FILENAME = "image_filename"

    const val MIN_PUBLISHED_YEAR = 0
    const val MAX_PUBLISHED_YEAR = 2100
}

private val DATE_FORMAT = SimpleDateFormat("EEE, MMM d yyy - hh:mm aaa", Locale.US)

@Entity(
    tableName = "book_table",
    indices = [
        Index(value = ["title"] ),
        Index(value = ["date_added"])
    ]
)
data class Book(@PrimaryKey(autoGenerate=true) val id: Int = 0): Parcelable {
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

    // Handles date added conversion type:
    // It's imported from json as a String encoded number of seconds. That's also how it
    // is stored in the database. The private _dateAdded stores the db value, the public
    // dateAdded returns it properly formatted.
    @ColumnInfo(name = "date_added")
    var raw_dateAdded = 0L

    val dateAdded: String
        get() = if (raw_dateAdded == 0L) ""
                else DATE_FORMAT.format(Date(raw_dateAdded * 1000))

    @ColumnInfo(name = "image_filename")
    var imageFilename = ""

    constructor(parcel: Parcel) : this(parcel.readInt()) {
        val obj: JSONObject = JSONTokener(parcel.readString()).nextValue() as JSONObject
        fromJson(obj)
    }

    constructor(title: String, author: String, imgUrl: String = "") : this() {
        this.title = title
        this.author = author
        this.imgUrl = imgUrl
    }

    constructor(o: JSONObject) : this() {
        fromJson(o)
    }

    private fun fromJson(obj: JSONObject) {
        this.title = obj.optString(BookFields.TITLE, "")
        this.subtitle = obj.optString(BookFields.SUBTITLE, "")
        this.author = obj.optString(BookFields.AUTHOR, "")
        this.publisher = obj.optString(BookFields.PUBLISHER, "")
        this.imgUrl = obj.optString(BookFields.UPLOADED_IMAGE_URL, "")
        this.physicalLocation = obj.optString(BookFields.PHYSICAL_LOCATION, "")
        this.isbn = obj.optString(BookFields.ISBN, "")
        this.summary = obj.optString(BookFields.SUMMARY, "")
        this.yearPublished = obj.optString(BookFields.YEAR_PUBLISHED, "")
        this.numberOfPages = obj.optString(BookFields.NUMBER_OF_PAGES, "")
        this.genre = obj.optString(BookFields.GENRE, "")
        this.language = obj.optString(BookFields.LANGUAGE, "")
        this.raw_dateAdded = obj.optLong(BookFields.DATE_ADDED, 0)
        this.imageFilename = obj.optString(BookFields.IMAGE_FILENAME, "")
    }

    private fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put(BookFields.TITLE, title)
        obj.put(BookFields.SUBTITLE, subtitle)
        obj.put(BookFields.AUTHOR, author)
        obj.put(BookFields.PUBLISHER, publisher)
        obj.put(BookFields.UPLOADED_IMAGE_URL, imgUrl)
        obj.put(BookFields.PHYSICAL_LOCATION, physicalLocation)
        obj.put(BookFields.ISBN, isbn)
        obj.put(BookFields.SUMMARY, summary)
        obj.put(BookFields.YEAR_PUBLISHED, yearPublished)
        obj.put(BookFields.NUMBER_OF_PAGES, numberOfPages)
        obj.put(BookFields.GENRE, genre)
        obj.put(BookFields.LANGUAGE, language)
        obj.put(BookFields.DATE_ADDED, raw_dateAdded)
        obj.put(BookFields.IMAGE_FILENAME, imageFilename)
        return obj
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
            BookFields.IMAGE_FILENAME -> imageFilename
            else -> "UNKNOWN KEY $key"
        }
    }

    // Parcelable.
    override fun describeContents(): Int {
        return 0
    }

    // Parcelable
    override fun writeToParcel(dest: Parcel, flags: Int) {
        val jsonString = toJson().toString()
        dest.writeString(jsonString)
    }

    companion object CREATOR : Parcelable.Creator<Book> {
        override fun createFromParcel(parcel: Parcel): Book {
            return Book(parcel)
        }

        override fun newArray(size: Int): Array<Book?> {
            return arrayOfNulls(size)
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
