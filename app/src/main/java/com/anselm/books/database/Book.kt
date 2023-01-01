package com.anselm.books.database

import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.*

object BookFields {
    const val BOOK_ID = "book_id" // Virtual field, for DetailsFragment only.
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
    const val LAST_MODIFIED = "last_modified"
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
data class Book(@PrimaryKey(autoGenerate=true) val id: Long = 0): Parcelable {
    @ColumnInfo(name = "title")
    var title = ""

    @ColumnInfo(name = "subtitle")
    var subtitle = ""

    @ColumnInfo(name = "imgUrl")
    var imgUrl = ""

    @ColumnInfo(name = "ISBN")
    var isbn = ""

    @ColumnInfo(name = "summary")
    var summary = ""

    @ColumnInfo(name = "yearPublished")
    var yearPublished = ""

    @ColumnInfo(name = "numberOfPages")
    var numberOfPages = ""

    @ColumnInfo(name = "language")
    var language = ""

    // Handles date added conversion type:
    // It's imported from json as a String encoded number of seconds. That's also how it
    // is stored in the database. The private _dateAdded stores the db value, the public
    // dateAdded returns it properly formatted.
    @ColumnInfo(name = "date_added")
    var rawDateAdded = 0L

    private val dateAdded: String
        get() = if (rawDateAdded == 0L) ""
                else DATE_FORMAT.format(Date(rawDateAdded * 1000))

    @ColumnInfo(name = "last_modified")
    var rawLastModified = 0L

    private val lastModified: String
        get() = if (rawLastModified == 0L) ""
        else DATE_FORMAT.format(Date(rawLastModified * 1000))

    @ColumnInfo(name = "image_filename")
    var imageFilename = ""

    constructor(parcel: Parcel) : this(parcel.readLong()) {
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

    private fun arrayToLabels(type: Label.Type, obj: JSONObject, key: String) {
        val values = obj.optJSONArray(key)
        if (values != null) {
            for (i in 0 until values.length()) {
                addLabel(type, values.optString(i))
            }
        }
    }

    private fun fromJson(obj: JSONObject) {
        this.title = obj.optString(BookFields.TITLE, "")
        this.subtitle = obj.optString(BookFields.SUBTITLE, "")
        this.publisher = obj.optString(BookFields.PUBLISHER, "")
        this.imgUrl = obj.optString(BookFields.UPLOADED_IMAGE_URL, "")
        this.physicalLocation = obj.optString(BookFields.PHYSICAL_LOCATION, "")
        this.isbn = obj.optString(BookFields.ISBN, "")
        this.summary = obj.optString(BookFields.SUMMARY, "")
        this.yearPublished = obj.optString(BookFields.YEAR_PUBLISHED, "")
        this.numberOfPages = obj.optString(BookFields.NUMBER_OF_PAGES, "")
        this.language = obj.optString(BookFields.LANGUAGE, "")
        this.rawDateAdded = obj.optLong(BookFields.DATE_ADDED, 0)
        this.imageFilename = obj.optString(BookFields.IMAGE_FILENAME, "")
        this.rawLastModified = obj.optLong(BookFields.LAST_MODIFIED, 0)
        // Handles multi-value fields:
        arrayToLabels(Label.Type.Authors, obj, "author")
        arrayToLabels(Label.Type.Genres, obj, "genre")
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
        obj.put(BookFields.DATE_ADDED, rawDateAdded)
        obj.put(BookFields.IMAGE_FILENAME, imageFilename)
        obj.put(BookFields.LAST_MODIFIED, rawLastModified)
        return obj
    }

    fun get(key: String): String {
        return when(key) {
            BookFields.BOOK_ID -> id.toString()
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
            BookFields.LAST_MODIFIED -> lastModified
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

    /**
     * Handling labels for this book.
     */
    @Ignore
    var labels: MutableList<Label>? = if (id == 0L) mutableListOf() else null
        private set
    @Ignore
    private var decorated = (id == 0L)
    @Ignore
    var labelsChanged = false
        private set

    fun decorate(databaseLabels: List<Label>): List<Label> {
        synchronized(this) {
            assert(this.labels == null)
            if ( ! decorated ) {
                this.labels = mutableListOf()
                this.labels!!.addAll(databaseLabels)
                decorated = true
            }
        }
        return this.labels!!
    }

    private fun addLabel(type: Label.Type, name: String) = addLabel(Label(type, name))

    private fun addLabel(label: Label) {
        check(decorated)
        labels!!.add(label)
        labelsChanged = true
    }

    private fun addLabels(labels: List<Label>) {
        check(decorated)
        this.labels!!.addAll(labels)
        labelsChanged = true
    }

    private fun clearLabels(type: Label.Type) {
        check(decorated)
        labels = labels?.filter {
            labelsChanged = labelsChanged || (it.type == type)
            it.type != type
        }?.toMutableList()
    }

    fun getLabels(type: Label.Type): List<Label> {
        check(decorated)
        return labels!!.filter { it.type == type }
    }

    fun firstLabel(type: Label.Type): Label? {
        check(decorated)
        return labels!!.firstOrNull { it.type == type }
    }

    private fun setOrReplaceLabel(type: Label.Type, tag: Label?) {
        check(decorated)
        val index = labels!!.indexOfFirst { it.type == type }
        if (index >= 0) {
            labels!!.removeAt(index)
            labelsChanged = true
        }
        if (tag != null) {
            labels!!.add(tag)
            labelsChanged = true
        }
    }

    private fun setOrReplaceLabels(type: Label.Type, labels: List<Label>?) {
        clearLabels(type)
        if (labels != null) {
            addLabels(labels)
        }
    }

    var publisher: String
        get() {
            check(decorated)
            return labels!!.firstOrNull { it.type == Label.Type.Publisher }?.name ?: ""
        }
        set(value) {
            setOrReplaceLabel(Label.Type.Publisher, Label(Label.Type.Publisher,value))
        }

    var publishers: List<Label>
        get() = getLabels(Label.Type.Publisher)
        set(value) {
            setOrReplaceLabels(Label.Type.Publisher, value)
        }

    var physicalLocation: String
        get() {
            check(decorated)
            return labels!!.firstOrNull { it.type == Label.Type.Location }?.name ?: ""
        }
        set(value) {
            setOrReplaceLabel(Label.Type.Location, Label(Label.Type.Location, value))
        }

    var locations: List<Label>
        get() = getLabels(Label.Type.Location)
        set(value) {
            setOrReplaceLabels(Label.Type.Location, value)
        }

    var genre: String = ""
        get() = getLabels(Label.Type.Genres).joinToString { it -> it.name }

    var genres: List<Label>
        get() = getLabels(Label.Type.Genres)
        set(value) {
            setOrReplaceLabels(Label.Type.Genres, value)
        }

    var author: String = ""
        get() = getLabels(Label.Type.Authors).joinToString { it -> it.name }

    var authors: List<Label>
        get() = getLabels(Label.Type.Authors)
        set(value) {
            setOrReplaceLabels(Label.Type.Authors, value)
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
