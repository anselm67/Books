package com.anselm.books

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.heifwriter.HeifWriter
import com.anselm.books.database.Book
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

    fun getCoverPath(book: Book): String {
        check(book.imageFilename.isNotEmpty()) { "getCoverPath requires a book imageFilename."}
        return File(basedir, book.imageFilename).path
    }

    /**
     * Fetches the book cover from its URL when needed.
     * Checks if the book's cover has already been loaded; If not fetches it, converts it
     * to the HEIF format, and stores it locally so it'll be included if you exportZip.
     */
    suspend fun fetchCover(book: Book, onImageSaved: (Book, String) -> Unit) {
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
            // We have some other chances to fetch the cover, e.g. through data cleansing.
            val bitmap = GlideApp.with(context)
                .asBitmap()
                .load(book.imgUrl)
                .submit()
                .get()
            val path = convertAndSave(book, bitmap)
            onImageSaved(book, path)
        }
    }

    suspend fun convertAndSave(book: Book, origBitmap: Bitmap): String {
        val file = getFileFor(book)
        file.parentFile?.mkdirs()

        val bitmap = resize(origBitmap)
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { outputStream ->
                HeifWriter.Builder(
                    outputStream.fd,
                    bitmap.width, bitmap.height,
                    HeifWriter.INPUT_MODE_BITMAP
                )
                .build().apply {
                    start()
                    addBitmap(resize(bitmap))
                    stop(0)
                    close()
                }
            }
        }
        if ( book.imageFilename.isNotEmpty() ) {
            File(basedir, book.imageFilename).delete()
        }
        return file.relativeTo(basedir).path
    }

    /**
     * Computes a unique image filename for this book.
     * We change the imageFilename every time this function is called. this is intended.
     * This is intended to defeat the Glide cache that doesn't offer a way to invalidate a
     * cache entry.
     */
    private fun getFileFor(book: Book): File {
        // Doing our best to generate a random string id for this book.
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val input = "$now:${book.title}:${book.authors.joinToString { it.name }}"
        val md = MessageDigest.getInstance("MD5")
        val path = BigInteger(1, md.digest(input.toByteArray()))
            .toString(16).padStart(32, '0').uppercase()
        val sep = File.separator
        return File(basedir, "$imageDirectoryName${sep}${path.substring(0, 2)}${sep}${path}")
    }

    private fun resize(bitmap:Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(
            bitmap,
            Constants.IMAGE_SIZE.width,
            Constants.IMAGE_SIZE.height,
            true
        )
    }
}