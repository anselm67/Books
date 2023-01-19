package com.anselm.books.ui.cleanup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import com.anselm.books.databinding.CleanupHeaderLayoutBinding
import com.anselm.books.databinding.CleanupItemLayoutBinding
import com.anselm.books.databinding.FragmentCleanupBinding
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

class CleanUpFragment: BookFragment() {
    private var _binding: FragmentCleanupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentCleanupBinding.inflate(inflater, container, false)


        viewLifecycleOwner.lifecycleScope.launch {
            val count = app.repository.deleteUnusedLabels()
            Log.d(TAG, "Deleted $count unused labels.")
            bookSection(inflater, binding.idStatsContainer)
            labelSection(inflater, binding.idStatsContainer)
            imageSection(inflater, binding.idStatsContainer)
        }

        super.handleMenu()
        return binding.root
    }

    private suspend fun bookSection(inflater: LayoutInflater, container: ViewGroup) {
        // Book section.
        container.addView(header(
            inflater,
            container,
            getString(R.string.book_count,app.repository.getTotalCount()),
        ))
        // Duplicate books.
        var count = app.repository.getDuplicateBookCount()
        if (count > 0) {
            container.addView(bookItem(
                inflater,
                container,
                getString(R.string.duplicate_books_cleanup, count)
            ) {
                app.repository.getDuplicateBookIds()
            })
        }
        // Books without cover images.
        count = app.repository.getWithoutCoverBookCount()
        if (count > 0) {
            container.addView(bookItem(
                inflater, container,
                getString(R.string.without_cover_books_cleanup, count)
            ) {
                app.repository.getWithoutCoverBookIds()
            })
        }
        // Books without certain label type.
        count = app.repository.getWithoutLabelBookCount(Label.Type.Authors)
        if (count > 0) {
            container.addView(bookQueryItem(
                inflater, container,
                getString(R.string.without_authors_cleanup, count),
                Query(withoutLabelOfType = Label.Type.Authors),
            ))
        }
        count = app.repository.getWithoutLabelBookCount(Label.Type.Genres)
        if (count > 0) {
            container.addView(bookQueryItem(
                inflater, container,
                getString(R.string.without_genres_cleanup, count),
                Query(withoutLabelOfType = Label.Type.Genres),
            ))
        }
        count = app.repository.getWithoutLabelBookCount(Label.Type.Location)
        if (count > 0) {
            container.addView(bookQueryItem(
                inflater, container,
                getString(R.string.without_locations_cleanup, count),
                Query(withoutLabelOfType = Label.Type.Location),
            ))
        }
        count = app.repository.getWithoutLabelBookCount(Label.Type.Language)
        if (count > 0) {
            container.addView(bookQueryItem(
                inflater, container,
                getString(R.string.without_languages_cleanup, count),
                Query(withoutLabelOfType = Label.Type.Language)
            ))
        }
    }

    private suspend fun labelSection(inflater: LayoutInflater, container: ViewGroup) {
        container.addView(header(inflater, container,getString(R.string.labels_cleanup_header)))
        val types = app.repository.getLabelTypeCounts()
        listOf(
            Pair(R.string.authors_cleanup, Label.Type.Authors),
            Pair(R.string.genres_cleanup, Label.Type.Genres),
            Pair(R.string.publishers_cleanup, Label.Type.Publisher),
            Pair(R.string.languages_cleanup, Label.Type.Language),
            Pair(R.string.locations_cleanup, Label.Type.Location),
        ).map { (stringId, type) ->
            container.addView(labelItem(
                inflater, container,
                getString(stringId,
                    types.firstOrNull { it.type == type }?.count ?: 0),
                type,
            ))
        }
    }

    private fun header( inflater: LayoutInflater, container: ViewGroup, title: String,): View {
        val header = CleanupHeaderLayoutBinding.inflate(inflater, container, false)
        header.idHeader.text = title
        return header.root
    }

    private fun item(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        onClick: (() -> Unit)? = null,
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        onClick?.let { item.idItemText.setOnClickListener { it() } }
        return item.root
    }

    private fun bookQueryItem(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        query: Query,
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        item.idItemText.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val action = CleanUpFragmentDirections.toSearchFragment(query)
                findNavController().navigate(action)
            }
        }
        return item.root
    }

    private fun bookItem(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        getItemIds: suspend () -> List<Long>,
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        item.idItemText.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val action = CleanUpFragmentDirections.toPagerFragment(
                    getItemIds().toLongArray(), 0
                )
                findNavController().navigate(action)
            }
        }
        return item.root
    }

    private fun labelItem(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        labelType: Label.Type
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        item.idItemText.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val action = CleanUpFragmentDirections.toCleanupLabelFragment(labelType)
                findNavController().navigate(action)
            }
        }
        return item.root
    }

    private fun imageSection(
        inflater: LayoutInflater,
        container: ViewGroup,
    ) {
        container.addView(header(
            inflater,
            container,
            getString(R.string.cleanup_book_cover_section),
        ))
        container.addView(item(
            inflater, container,
            getString(R.string.check_for_broken_images)
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                checkImages()
            }
        })
    }

    class FixCoverStats(
        var totalCount : Int = 0,       // Total number of books seen.
        var checkedCount: Int = 0,      // Total number cover image checked.
        var brokenCount: Int = 0,       // Number of un-loadable bitmaps from files.
        var unfetchedCount: Int = 0,    // Number of covers that weren't fetched.
        var fetchCount: Int = 0,        // Number of covers we fetched.
        var fetchFailedCount: Int = 0   // Number of failed cover fetches.
    ) {
        private val calls = emptyList<Call>().toMutableList()
        private val lock = ReentrantLock()
        private val cond = lock.newCondition()

        fun cancel() {
            calls.forEach { it.cancel() }
        }

        fun addCall(call: Call?) {
            call?.let {
                lock.withLock {
                    calls.add(it)
                }
            }
        }

        fun removeCall(call: Call) {
            lock.withLock {
                calls.remove(call)
                if (calls.isEmpty()) {
                    Log.d(TAG, "cond signaled.")
                    cond.signalAll()
                }
            }
        }

        fun join() {
            while (true) {
                lock.withLock {
                    if (calls.isEmpty()) {
                        return@join
                    }
                    try {
                        cond.await(500, TimeUnit.MILLISECONDS)
                    } catch (e: InterruptedException) { /* ignored */ }
                    Log.d(TAG, "cond awaited, empty? ${calls.isEmpty()}")
                }
            }
        }
    }

    private fun saveCover(book:Book, bitmap: Bitmap) {
        app.applicationScope.launch {
            book.imageFilename = app.imageRepository.convertAndSave(book, bitmap)
            app.repository.save(book)
        }
    }

    private fun fixCover(stats: FixCoverStats, book: Book): Call? {
        // If the book doesn't have a URL, there's nothing we can do.
        if (book.imgUrl.isEmpty()) {
            return null
        }

        // Fetches the bitmap, and re-save the image back to a (new) file.
        val call = app.okHttp.newCall(
            Request.Builder()
                .header("Accept", "*/*")
                .url(book.imgUrl)
                .build()
        )
        stats.fetchCount++
        stats.addCall(call)
        call.enqueue(object: Callback {
            // By doing nothing, we're doing all that needs get done.
            override fun onFailure(call: Call, e: IOException) {
                stats.removeCall(call)
                stats.fetchFailedCount++
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    stats.removeCall(call)
                    if (response.isSuccessful && response.body != null) {
                        val bitmap = BitmapFactory.decodeStream(response.body!!.byteStream())
                        if (bitmap != null) {
                            saveCover(book, bitmap)
                        } else {
                            stats.fetchFailedCount++
                        }
                    } else {
                        stats.fetchFailedCount++
                    }
                }
            }
        })
        return call
    }

    private fun checkImage(stats: FixCoverStats, book: Book): Call? {
        // Nothing we can do without an url to fetch the image from.
        if (book.imgUrl.isEmpty()) {
            return null
        }
        if (book.imageFilename.isEmpty()) {
            // The book's image was never loaded, we can fix this.
            stats.unfetchedCount++
            return fixCover(stats, book)
        } else /* book.imageFilename.isNotEmpty() */ {
            // Verifies we can load this bitmap and fix if we can't.
            val path = app.imageRepository.getCoverPath(book)
            var failed = true
            try {
                failed = (BitmapFactory.decodeFile(path) == null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode bitmap.")
            }
            if (failed) {
                stats.brokenCount++
                return fixCover(stats, book)
            }
        }
        return null
    }

    private suspend fun checkImages() {
        val bookIds = app.repository.getIdsList(Query())
        val stats = FixCoverStats()

        val progressSetter = app.loadingDialog(
            requireActivity()) {
            stats.cancel()
        }
        bookIds.forEach { bookId ->
            val book = app.repository.load(bookId, decorate = true)
            stats.totalCount++
            if (book != null) {
                stats.checkedCount++
                checkImage(stats, book)
            }
            val done = stats.totalCount.toFloat() / bookIds.size.toFloat()
            progressSetter((100.0F * done).roundToInt())
        }
        // Wait until al calls have returned.
        stats.join()
        Log.d(TAG, "Done ${stats.totalCount}: " +
                "broken: ${stats.brokenCount}, unfetched: ${stats.unfetchedCount} " +
                "fetched: ${stats.fetchCount} of which ${stats.fetchFailedCount} failed."
        )
    }
}