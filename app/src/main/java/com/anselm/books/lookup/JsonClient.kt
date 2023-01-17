package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.TAG
import com.anselm.books.database.Book
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField

abstract class JsonClient: SimpleClient() {

    protected inline fun <reified T: Any> arrayToList(a: JSONArray?): List<T> {
        return if (a == null) {
            emptyList()
        } else {
            (0 until a.length()).map { (a.get(it) as T) }
        }
    }

    private val dateFormatters = arrayOf(
        DateTimeFormatter.ofPattern("MMMM d, yyyy"),
        DateTimeFormatter.ofPattern("MMMM yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM"),
        DateTimeFormatter.ofPattern("yyyy MMMM"),
        DateTimeFormatter.ofPattern("yyyy"),
    )

    protected fun publishDate(s: String): String {
        for (fmt in dateFormatters) {
            try {
                val date = fmt.parse(s)
                if (date != null) {
                    return date.get(ChronoField.YEAR).toString()
                }
            } catch (e: DateTimeParseException) {
                // Ignored.
            }
        }
        Log.d(TAG, "Failed to parse date: $s.")
        return ""
    }
    override fun handleResponse(
        resp: Response,
        onError: (message: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit,
        onSuccess: ((JSONObject) -> Unit)?
    ) {
        val url = resp.request.url
        if (resp.isSuccessful) {
            val tok = JSONTokener(resp.body!!.string())
            val obj = tok.nextValue()
            if (obj !is JSONObject) {
                onError("$url: parse failed got a ${obj.javaClass.name}.", null)
            } else {
                onSuccess?.invoke(obj)
            }
        } else {
            if (resp.code == 404) {
                //That's a no-match.
                onBook(null)
            } else {
                // A real error.
                onError("$url: HTTP request failed, status $resp", null)
            }
        }
    }
}