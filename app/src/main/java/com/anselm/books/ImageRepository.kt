package com.anselm.books

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.heifwriter.HeifWriter
import com.anselm.books.database.Book
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

class ImageRepository(
    private val context: Context,
    private val basedir: File,
) {
    // Name of the directory in which images are saved.
    // This name is the same within the exported zip, and it has to be that way.
    val imageDirectoryName = "images"
    val imageDirectory = File(basedir, "images")

    fun getCoverUri(book: Book): Uri? {
        return if (book.imageFilename != "") {
            File(basedir, book.imageFilename).toUri()
        } else if (book.imgUrl != "") {
            Uri.parse(book.imgUrl)
        } else {
            null
        }
    }

    /**
     * Ensures this book cover is saved, fetches and saves it if needed.
     */
    suspend fun saveCover(book: Book, onImageSaved: (Book, String) -> Unit) {
        // Finds the book URL, if none we're done.
        if (book.imgUrl.isEmpty()) {
            return
        }
        // Finds the book's image filename and checks it, we might have it already.
        if (book.imageFilename.isNotEmpty() && File(basedir, book.imageFilename).exists()) {
            return
        }
        // Gets the work done.
        withContext(Dispatchers.IO) {
            // We ignore errors, cause quite frankly we don't know what to do anyways.
            // We'' have some other chances to fetch the cover, e.g. through data cleansing.
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(book.imgUrl) // sample image
                .submit()
                .get()
            val path = convertAndSave(book, bitmap)
            onImageSaved(book, path)
        }
    }

    private suspend fun convertAndSave(book: Book, bitmap: Bitmap): String {
        val path = getImageFilenameFor(book)
        val sep = File.separator
        val file = File(basedir, "$imageDirectoryName${sep}${path.substring(0, 2)}${sep}${path}")
        file.parentFile?.mkdirs()

        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { outputStream ->
                HeifWriter.Builder(
                    outputStream.fd,
                    bitmap.width, bitmap.height,
                    HeifWriter.INPUT_MODE_BITMAP
                )
                .build().apply {
                    start()
                    addBitmap(bitmap)
                    stop(0)
                    close()
                }
            }
        }
        return file.relativeTo(basedir).path
    }

    private fun getImageFilenameFor(book: Book): String {
        // Doing our best to generate a random string id for this book.
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val input = "$now:${book.title}:${book.authors.joinToString { it.name }}"
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray()))
            .toString(16).padStart(32, '0').uppercase()
    }
}