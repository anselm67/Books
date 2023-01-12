package com.anselm.books.ui.scan

import android.annotation.SuppressLint
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.databinding.FragmentScanBinding
import com.anselm.books.databinding.RecyclerviewScanIsbnBinding
import com.anselm.books.ui.widgets.BookFragment
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import okhttp3.Call
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ScanFragment: BookFragment() {
    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var adapter: IsbnArrayAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentScanBinding.inflate(inflater, container, false)

        // Sets up the recycler view.
        adapter = IsbnArrayAdapter({ updateLookupStats(it)  }, { onLookupResultClick(it) })
        binding.idRecycler.adapter = adapter
        binding.idRecycler.layoutManager = LinearLayoutManager(binding.idRecycler.context)

        ItemTouchHelper(ScanItemTouchHelper(adapter)).attachToRecyclerView(binding.idRecycler)
        // For now, that's your only option out of scanning.
        binding.idDoneButton.setOnClickListener {
            saveAllMatches()
            findNavController().popBackStack()
        }

        // Checks permissions and sets up the camera.
        if ( checkCameraPermission()) {
            startCamera()
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        return binding.root
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Sets up the preview use case.
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.idViewFinder.surfaceProvider)
                }

            // Sets up the image analyzer user case.
            val barcodeAnalyzer = BarcodeAnalyzer(binding.idOverlay) {
                handleISBN(it)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, barcodeAnalyzer)
                }

            try {
                // Binds our use cases to the camera after clearing out any previous binding.
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
                barcodeAnalyzer.scaleFor(imageAnalyzer.resolutionInfo!!, binding.idViewFinder)
            } catch(e: Exception) {
                Log.e(TAG, "Failed to bind the camera", e)
                app.toast(getString(R.string.bind_camera_failed, e.message))
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun handleISBN(isbn: String) {
        playSound()
        adapter.insertFirst(isbn)
        binding.idRecycler.scrollToPosition(0)
    }

    private fun updateLookupStats(stats: LookupStats) {
        // Updates the display of the stats.
        binding.idMessageText.text = app.getString(
            R.string.scan_stat_message,
            stats.lookupCount.get(),
            stats.matchCount.get(),
            stats.noMatchCount.get(),
            stats.errorCount.get(),
        )
    }

    private fun playSound() {
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(requireContext(), notification)
        ringtone.play()
    }

    private fun saveAllMatches() {
        // Is there any work to do?
        val books = adapter.getAllBooks()
        if (books.isEmpty())
            return
        // Lets run with it.
        app.applicationScope.launch {
            books.forEach {
                app.repository.save(it)
            }
            app.toast(app.getString(R.string.scan_books_added, books.size))
        }
    }

    private fun onLookupResultClick(result: LookupResult) {
        // We do nothing and that's fine for now.
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}

class LookupResult(
    var call: Call? = null,
    var book: Book? = null,
    var exception: Exception? = null,
    var errorMessage: String? = null,
) {
    val loading get() = (call != null)
}

data class LookupStats(
    val lookupCount: AtomicInteger = AtomicInteger(0),
    val errorCount: AtomicInteger = AtomicInteger(0),
    val matchCount: AtomicInteger = AtomicInteger(0),
    val noMatchCount: AtomicInteger = AtomicInteger(0),
)

class IsbnArrayAdapter(
    private val statsListener: (LookupStats) -> Unit,
    private val onClick: (LookupResult) -> Unit,
): RecyclerView.Adapter<IsbnArrayAdapter.ViewHolder>() {
    private val dataSource = mutableListOf<Pair<String, LookupResult>>()
    private val stats = LookupStats()

    inner class ViewHolder(
        val binding: RecyclerviewScanIsbnBinding,
    ): RecyclerView.ViewHolder(binding.root) {

        private fun updateStatus(loading: Boolean, checked: Boolean, error: Boolean) {
            binding.idLoadProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.idCheckMark.visibility = if (checked) View.VISIBLE else View.GONE
            binding.idErrorMark.visibility = if (error) View.VISIBLE else View.GONE
        }

        fun bind(item: Pair<String, LookupResult>) {
            // We always have at least an ISBN to display.
            val (isbn, result) = item
            binding.idISBNText.text = isbn
            binding.root.setOnClickListener { onClick(result) }
            if (result.loading) {
                updateStatus(true, checked = false, error = false)
                return
            }
            // Results are back.
            if (result.book != null) {
                // A match wa found, fill in the bindings.
                binding.idTitleText.text = result.book!!.title
                binding.idAuthorText.text = result.book!!.authors.joinToString { it.name }
                val uri = app.imageRepository.getCoverUri(result.book!!)
                if (uri != null) {
                    Glide.with(app.applicationContext)
                        .load(uri)
                        .placeholder(R.drawable.broken_image_icon)
                        .centerCrop()
                        .into(binding.idCoverImage)
                } else {
                    Glide.with(app.applicationContext)
                        .load(R.mipmap.ic_book_cover)
                        .centerCrop()
                        .into(binding.idCoverImage)
                }
                // Hide the progress, replace it with the checkmark.
                updateStatus(loading = false, checked = true, error = false)
                binding.idCheckMark.visibility = View.VISIBLE
            } else {
                // Clears the image in case this holder was used by a match before.
                Glide.with(app.applicationContext)
                    .load(R.mipmap.ic_book_cover)
                    .centerCrop()
                    .into(binding.idCoverImage)
                // Some kind of error occurred: no match or a real error.
                if ( result.errorMessage != null ) {
                    binding.idTitleText.text = result.errorMessage ?: result.exception!!.message
                } else {
                    binding.idTitleText.text = app.getString(R.string.no_match_found)
                    binding.idAuthorText.text = app.getString(R.string.manual_input_required)
                }
                updateStatus(false, checked = false, error = true)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RecyclerviewScanIsbnBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataSource[position])
    }

    override fun getItemCount(): Int {
        return dataSource.size
    }

    fun removeAt(position: Int) {
        check (position >= 0 && position < dataSource.size)
        val (_, lookup) = dataSource[position]
        if (lookup.call != null) {
            lookup.call!!.cancel()
            lookup.call = null
        }
        dataSource.removeAt(position)
        notifyItemRemoved(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun insertFirst(isbn: String) {
        val lookup = LookupResult()
        dataSource.add(0, Pair(isbn, lookup))
        notifyItemInserted(0)
        stats.lookupCount.incrementAndGet()
        lookup.call = app.lookup(isbn, { msg, e ->
            Log.e(TAG, "Failed to lookup $isbn.", e)
            lookup.call = null
            stats.errorCount.incrementAndGet()
            lookup.exception = e
            lookup.errorMessage = msg
            app.postOnUiThread {
                notifyDataSetChanged()
                statsListener(stats)
            }
        }, { book ->
            if (book == null) {
                stats.noMatchCount.incrementAndGet()
            } else {
                stats.matchCount.incrementAndGet()
            }
            lookup.call = null
            lookup.book = book
            app.postOnUiThread {
                notifyDataSetChanged()
                statsListener(stats)
            }
        })
    }

    fun getAllBooks(): List<Book> {
        return dataSource.mapNotNull { (_, result) -> result.book }
    }
}
