package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.TAG
import com.anselm.books.database.Book
import okhttp3.internal.headersContentLength
import java.io.IOException

class AmazonImageClient: SimpleClient() {

    override fun lookup(tag: String, book: Book, onCompletion: () -> Unit) {
        if (book.imgUrl.isNotEmpty() || book.isbn.length <= 3) {
            onCompletion()
        } else {
            val key = book.isbn.substring(3)
            val url = "http://images.amazon.com/images/P/$key.01Z.jpg"

            request(tag, url, useHead = true)
                .onResponse { response ->
                    if (response.isSuccessful && response.headersContentLength() > 50) {
                        book.imgUrl = url
                    }
                    onCompletion()
                }
                .onError { e: IOException ->
                    Log.e(TAG, "$url http request failed.", e)
                    onCompletion()
                }
                .run()
        }
    }
}

