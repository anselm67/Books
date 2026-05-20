package com.anselm.books.ui.cleanup

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.anselm.books.BooksApplication
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.BooksApplication.Reporter
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Query
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.launch
import okhttp3.Call
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class CleanUpFragment: BookFragment() {
    private var reporter: Reporter? = null

    private fun ifEmptyReporter(block: () -> Unit) {
        if (reporter == null) {
            block()
        } else {
            app.warn(requireActivity(), getString(R.string.cleanup_task_already_running))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        super.handleMenu()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    CleanUpScreen(
                        onNavigateToPager = { query ->
                            findNavController().navigate(
                                CleanUpFragmentDirections.toPagerFragment(query = query)
                            )
                        },
                        onNavigateToSearch = { query ->
                            findNavController().navigate(
                                CleanUpFragmentDirections.toSearchFragment(query)
                            )
                        },
                        onNavigateToLabelCleanup = { type ->
                            findNavController().navigate(
                                CleanUpFragmentDirections.toCleanupLabelFragment(type)
                            )
                        },
                        onCheckImages = {
                            ifEmptyReporter {
                                viewLifecycleOwner.lifecycleScope.launch { checkImages() }
                            }
                        },
                        onDeleteUnusedImages = {
                            ifEmptyReporter { deleteUnusedImages() }
                        },
                    )
                }
            }
        }
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
        private val urls = emptyList<String>().toMutableList()
        private val lock = ReentrantLock()
        private val cond = lock.newCondition()

        fun cancel() {
            calls.forEach { it.cancel() }
        }

        fun addCall(call: Call) {
            calls.add(call)
        }

        fun addUrl(url: String) {
            lock.withLock {
                urls.add(url)
            }
        }

        fun removeUrl(url: String) {
            lock.withLock {
                urls.remove(url)
                if (urls.isEmpty()) {
                    Log.d(TAG, "cond signaled.")
                    cond.signalAll()
                }
            }
        }

        fun join() {
            while (true) {
                lock.withLock {
                    if (urls.isEmpty()) {
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

    private fun fixCover(stats: FixCoverStats, book: Book) {
        // If the book doesn't have a URL, there's nothing we can do.
        if (book.imgUrl.isEmpty()) {
            return
        }
        stats.fetchCount++
        app.applicationScope.launch {
            stats.addUrl(book.imgUrl)
            val call = app.imageRepository.save(book, force = true) {
                stats.removeUrl(book.imgUrl)
                if (it) {
                    app.applicationScope.launch {
                        app.repository.save(book)
                    }
                }
                if (book.imageFilename.isEmpty()) {
                    stats.fetchFailedCount++
                }
            }
            call?.let { stats.addCall(call) }
        }
    }

    private fun checkImage(stats: FixCoverStats, book: Book) {
        // Nothing we can do without an url to fetch the image from.
        if (book.imgUrl.isEmpty()) {
            return
        }
        if (book.imageFilename.isEmpty()) {
            // The book's image was never loaded, we can fix this.
            stats.unfetchedCount++
            fixCover(stats, book)
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
                fixCover(stats, book)
            }
        }
    }

    private suspend fun checkImages() {
        val bookIds = app.repository.getIdsList(Query())
        val stats = FixCoverStats()

        reporter = app.openReporter(
            getString(R.string.cleanup_check_image_progress_title),
            isIndeterminate = false) { stats.cancel() }
        bookIds.forEach { bookId ->
            val book = app.repository.load(bookId, decorate = true)
            stats.totalCount++
            if (book != null) {
                stats.checkedCount++
                checkImage(stats, book)
            }
            reporter?.update(stats.totalCount, bookIds.size)
        }
        // Wait until all calls have returned.
        stats.join()
        reporter?.close()
        reporter = null
        Log.d(TAG, "Done ${stats.totalCount}: " +
                "broken: ${stats.brokenCount}, unfetched: ${stats.unfetchedCount} " +
                "fetched: ${stats.fetchCount} of which ${stats.fetchFailedCount} failed."
        )
    }

    private suspend fun doDeleteUnusedImages(reporter: BooksApplication.Reporter) {
        val bookIds = app.repository.getIdsList(Query())
        val seen = mutableSetOf<String>()
        var count = 0
        reporter.update(getString(R.string.listing_existing_images), 0, 0)
        bookIds.forEach {
            val book = app.repository.load(it, decorate = true)
            if (book?.imageFilename?.isNotEmpty() == true) {
                seen.add(book.imageFilename)
            }
            count++
            reporter.update(count, bookIds.size)
        }
        app.imageRepository.garbageCollect(seen, reporter)
    }

    private fun deleteUnusedImages() {
        thread {
            reporter = app.openReporter(getString(R.string.deleting_unused_images), isIndeterminate = false)
            app.applicationScope.launch {
                doDeleteUnusedImages(reporter!!)
                reporter?.close()
                reporter = null
            }
        }
    }
}
