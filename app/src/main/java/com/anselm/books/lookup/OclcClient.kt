package com.anselm.books.lookup

import android.util.Log
import android.util.Xml
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParser.TEXT
import org.xmlpull.v1.XmlPullParserException

class OclcClient: SimpleClient() {
    private val wsKey = "REDACTED"
    private var parser: XmlPullParser = Xml.newPullParser()

    private fun until(name: String, handle: (String) -> Unit) {
        while (parser.next() != END_TAG) {
            if (parser.eventType == START_TAG) {
                if (parser.name == name) {
                    if (parser.next() != TEXT) {
                        throw XmlPullParserException("Expected TEXT.")
                    } else {
                        handle(parser.text)
                        check(parser.next() == END_TAG)
                        return
                    }
                } else {
                    parser.next() // TEXT
                    parser.next() // END_TAG
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
        book: Book,
        src: String,
        text: String,
    ) {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(text.reader())
        parser.nextTag()
        when (parser.name) {
            "oclcdcs" -> {
                expect(TEXT)
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
                            setIfEmpty(book::title, value)
                        }
                        "dc:description" -> {
                            // We might get multiple of these, we just get the first one.
                            setIfEmpty(book::summary, value)
                        }
                        "dc:language" -> {
                            val lang = getLanguage(value)
                            setIfEmpty(book::language, app.repository.labelB(Label.Type.Language, lang))
                        }
                        "dc:format" -> {
                            setIfEmpty(book::numberOfPages, extractNumberOfPages(value))
                        }
                        "dc:date" -> {
                            setIfEmpty(book::yearPublished, extractYear(value))
                        }
                        "dc:publisher" -> {
                            setIfEmpty(book::publisher, app.repository.labelB(Label.Type.Publisher, value))
                        }
                        "dc:identifier" -> {
                            if (ISBN.isValidEAN13(value)) {
                                setIfEmpty(book::isbn, value)
                            }
                        }
                        else -> {
                            Log.d(TAG, "Unhandled tag: $name")
                        }
                    }
                    expect(TEXT)
                }
                setIfEmpty(book::authors, authors)
            }
            "diagnostics" -> {
                while (parser.next() != START_TAG) { /* Intended empty */ }
                check(parser.name == "diagnostic")
                until("message") {
                    Log.e(TAG, "$src: request failed, $it")
                }
            }
            else -> {
                Log.e(TAG, "$src: expecting a x or y tag, got ${parser.name}")
            }
        }
    }

    private val properties = listOf(
        Book::authors,
        Book::title,
        Book::summary,
        Book::language,
        Book::numberOfPages,
        Book::yearPublished,
        Book::publisher,
        Book::isbn
    )

    override fun lookup(
        tag: String,
        book: Book,
        onCompletion: () -> Unit,
    ) {
        if (hasAllProperties(book, properties)) {
            onCompletion()
            return
        }
        val url = "https://www.worldcat.org/webservices/catalog/content/isbn/${book.isbn}?wskey=$wsKey&maximumRecords=1&recordSchema=info:srw/schema/1/dc"
        request(tag, url)
            .onResponse {
                if (it.isSuccessful) {
                    parseXml(book, url, it.body!!.string())
                } else {
                    Log.e(TAG, "$url: HTTP request returned status ${it.code}")
                }
                onCompletion()
            }
            .onError {
                Log.e(TAG, "$url: http request failed.", it)
                onCompletion()
            }
            .run()
    }
}


