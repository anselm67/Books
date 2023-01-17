package com.anselm.books.lookup

import android.util.Log
import android.util.Xml
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import okhttp3.Response
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParser.TEXT
import org.xmlpull.v1.XmlPullParserException

class OclcClient: SimpleClient() {

    override fun handleResponse(
        resp: Response,
        onError: (message: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit,
        onSuccess: ((JSONObject) -> Unit)?
    ) {
        val url = resp.request.url
        if (resp.isSuccessful) {
            parseXml(
                url.toString(),
                resp.body!!.string(),
                onError,
                onBook,
            )
        } else {
            onError("$url: HTTP Request failed, status $resp", null)
        }
    }

    private val wsKey = "REDACTED"

    override fun lookup(
        tag: String,
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (matches: Book?) -> Unit
    ) {
        val url = "https://www.worldcat.org/webservices/catalog/content/isbn/$isbn?wskey=$wsKey&maximumRecords=1&recordSchema=info:srw/schema/1/dc"
        runRequest(tag, url, onError, onBook)
    }

    private var parser: XmlPullParser = Xml.newPullParser()

    private fun expectTag(name: String) {
        if (parser.next() != START_TAG || parser.name != name) {
            throw XmlPullParserException("Expected $name tag, got ${parser.name}")
        }
    }

    private fun until(name: String, handle: (String) -> Unit) {
        while (parser.next() != END_TAG) {
            if (parser.next() == START_TAG && parser.name == name) {
                if (parser.next() != TEXT) {
                    throw XmlPullParserException("Expected TEXT.")
                } else {
                    handle(parser.text)
                    check(parser.next() == END_TAG)
                    return
                }
            }
        }
    }

    private fun expect(expected: Int) {
        val got = parser.next()
        if (got == expected) {
            return
        } else {
            throw XmlPullParserException("Expected $expected, got $got.")
        }
    }

    private val languages = mapOf(
        "fre" to "French",
        "eng" to "English",
    )

    private fun getLanguage(code: String): String {
        return languages.getOrDefault(code, code)
    }

    private val numberOfPagesRE = Regex("^.*[\\s(\\[]+([0-9]+)[\\s)\\]]+p.*$", RegexOption.IGNORE_CASE)
    private fun extractNumberOfPages(format: String): String {
        val matchResult = numberOfPagesRE.matchEntire(format)
        return matchResult?.groups?.get(1)?.value ?: ""
    }

    private val yearRE = Regex(".*(^|\\s+|\\p{P}+|\\p{S}+)([0-9]{4})($|\\s+).*")
    private fun extractYear(s: String): String {
        val matchResult = yearRE.matchEntire(s)
        return matchResult?.groups?.get(2)?.value ?: ""
    }

    private fun parseXml(
        src: String,
        text: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit
    ) {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(text.reader())
        parser.nextTag()
        when (parser.name) {
            "oclcdcs" -> {
                expect(TEXT)
                val book = Book()
                val authors = emptyList<Label>().toMutableList()
                while (parser.next() == START_TAG) {
                    val name = parser.name
                    expect(TEXT)
                    val value = parser.text
                    expect(END_TAG)
                    when(name) {
                        "dc:creator", "dc:contributor" -> {
                            authors.add(app.repository.labelB(Label.Type.Authors, value))
                        }
                        "dc:title" -> {
                            book.title = value
                        }
                        "dc:description" -> {
                            // We might get multiple of these, we just concatenate them.
                            book.summary = book.summary + System.lineSeparator() + value
                        }
                        "dc:language" -> {
                            val lang = getLanguage(value)
                            book.language = app.repository.labelB(Label.Type.Language, lang)
                        }
                        "dc:format" -> {
                            book.numberOfPages = extractNumberOfPages(value)
                        }
                        "dc:date" -> {
                            book.yearPublished = extractYear(value)
                        }
                        "dc:publisher" -> {
                            book.publisher = app.repository.labelB(Label.Type.Publisher, value)
                        }
                        "dc:identifier" -> {
                            if ( app.isValidEAN13(value) ) {
                                book.isbn = value
                            }
                        }
                        else -> {
                            Log.d(TAG, "Unhandled tag: $name")
                        }
                    }
                    expect(TEXT)
                }
                book.authors = authors
                onBook(book)
            }
            "diagnostics" -> {
                expectTag("diagnostic")
                until("message") {
                    onError("$src: request failed, $it", null)
                }
            }
            else -> {
                onError("$src: expecting a x or y tag, got ${parser.name}", null)
            }
        }
    }
}


