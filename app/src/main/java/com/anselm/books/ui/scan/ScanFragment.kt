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
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.GlideApp
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.databinding.FragmentScanBinding
import com.anselm.books.databinding.RecyclerviewScanIsbnBinding
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ScanFragment: BookFragment() {
    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var adapter: IsbnArrayAdapter
    private lateinit var viewModel: ScanViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentScanBinding.inflate(inflater, container, false)

        val modelInitialized = ::viewModel.isInitialized
        val doCamera =  ! modelInitialized || ! viewModel.isDone
        viewModel = ViewModelProvider(this)[ScanViewModel::class.java]

        // Sets up the recycler view.
        adapter = IsbnArrayAdapter(
            viewModel.lookupResults,
            { updateLookupStats(it)  },
            { onLookupResultClick(it) },
            viewLifecycleOwner.lifecycleScope
        )
        binding.idRecycler.adapter = adapter
        binding.idRecycler.layoutManager = LinearLayoutManager(binding.idRecycler.context)
        ItemTouchHelper(ScanItemTouchHelper(adapter)).attachToRecyclerView(binding.idRecycler)

        // Starts the camera if we haven't stopped it already.
        if ( doCamera ) {
            // For now, that's your only option out of scanning.
            binding.idDoneButton.setOnClickListener {
                stopCamera()
            }

            // Checks permissions and sets up the camera.
            if (checkCameraPermission()) {
                startCamera()
            }
            cameraExecutor = Executors.newSingleThreadExecutor()
            binding.idSaveButton.isVisible = false
            binding.idDoneButton.isVisible = true
        } else {
            binding.idViewFinder.isVisible = false
            binding.idSaveButton.isVisible = true
            binding.idDoneButton.isVisible = false
        }
        if (modelInitialized && viewModel.stats != null) {
            updateLookupStats(viewModel.stats!!)
        }
        handleMenu()
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
        viewModel.stats = stats
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
        if (viewModel.isDone && result.book != null) {
            val action = ScanFragmentDirections.toDetailsFragment(book = result.book)
            findNavController().navigate(action)
        }
    }

    private fun stopCamera() {
        // Stops the camera.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(requireContext()))
        binding.idViewFinder.isVisible = false
        binding.idDoneButton.isVisible = false
        viewModel.isDone = true
        // Gets ready for some cleanup work.
        binding.idSaveButton.isVisible = true
        binding.idSaveButton.setOnClickListener {
            saveAllMatches()
            findNavController().popBackStack()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}


class LookupResult(
    val isbn: String,
    var tag: String? = null,
    var book: Book? = null,
    var isDuplicate: Boolean = false,
    var exception: Exception? = null,
    var errorMessage: String? = null,
) {
    val loading get() = (tag != null)
}

data class LookupStats(
    val lookupCount: AtomicInteger = AtomicInteger(0),
    val errorCount: AtomicInteger = AtomicInteger(0),
    val matchCount: AtomicInteger = AtomicInteger(0),
    val noMatchCount: AtomicInteger = AtomicInteger(0),
)

class IsbnArrayAdapter(
    private val dataSource: MutableList<LookupResult>,
    private val statsListener: (LookupStats) -> Unit,
    private val onClick: (LookupResult) -> Unit,
    private val viewScope: CoroutineScope,
): RecyclerView.Adapter<IsbnArrayAdapter.ViewHolder>() {
    private val stats = LookupStats()

    inner class ViewHolder(
        val binding: RecyclerviewScanIsbnBinding,
    ): RecyclerView.ViewHolder(binding.root) {

        private fun updateStatus(
            loading: Boolean = false,
            checked: Boolean = false,
            error: Boolean = false,
            duplicate: Boolean = false,
        ) {
            binding.idLoadProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.idCheckMark.visibility = if (checked) View.VISIBLE else View.GONE
            binding.idErrorMark.visibility = if (error) View.VISIBLE else View.GONE
            binding.idDuplicateMark.visibility = if (duplicate) View.VISIBLE else View.GONE
        }

        fun bind(result: LookupResult) {
            // We always have at least an ISBN to display.
            binding.idISBNText.text = result.isbn
            binding.root.setOnClickListener { onClick(result) }
            if (result.loading) {
                updateStatus(loading = true)
                return
            }
            // Results are back.
            if (result.book != null) {
                bindWithBook(result)
            } else {
                bindNotFound(result)
            }
        }

        private fun bindWithBook(result: LookupResult) {
            check(result.book != null) { "Expected a match i LookupResult."}
            // Launch duplicate detection which will update the status UI.
            // FIXME We should spare the lookup when it's already been done. eg from model.
            viewScope.launch {
                // Hide the progress, replace it with the checkmark.
                if (app.repository.getDuplicates(result.book!!).isEmpty()) {
                    updateStatus(checked = true)
                } else {
                    result.isDuplicate = true
                    updateStatus(duplicate = true)
                }
            }
            // Fills in the bindings.
            binding.idTitleText.text = result.book!!.title
            binding.idAuthorText.text = result.book!!.authors.joinToString { it.name }
            val uri = app.imageRepository.getCoverUri(result.book!!)
            if (uri != null) {
                GlideApp.with(app.applicationContext)
                    .load(uri)
                    .placeholder(R.mipmap.broken_cover_icon_foreground)
                    .into(binding.idCoverImage)
            } else {
                GlideApp.with(app.applicationContext)
                    .load(R.mipmap.ic_book_cover)
                    .into(binding.idCoverImage)
            }
        }

        private fun bindNotFound(result: LookupResult) {
            // Clears the image in case this holder was used by a match before.
            GlideApp.with(app.applicationContext)
                .load(R.mipmap.ic_book_cover)
                .into(binding.idCoverImage)
            // Some kind of error occurred: no match or a real error.
            if ( result.errorMessage != null ) {
                binding.idTitleText.text = result.errorMessage ?: result.exception!!.message
            } else {
                binding.idTitleText.text = app.getString(R.string.no_match_found)
                binding.idAuthorText.text = app.getString(R.string.manual_input_required)
            }
            updateStatus(error = true)
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
        val result = dataSource[position]
        if (result.tag != null) {
            app.cancelHttpRequests(result.tag!!)
        }
        dataSource.removeAt(position)
        notifyItemRemoved(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun insertFirst(isbn: String) {
        val lookup = LookupResult(isbn)
        dataSource.add(lookup)
        notifyItemInserted(0)
        stats.lookupCount.incrementAndGet()
        lookup.tag = app.lookup(isbn, { msg, e ->
            Log.e(TAG, "Failed to lookup $isbn.", e)
            lookup.tag = null
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
            lookup.tag = null
            lookup.book = book
            app.postOnUiThread {
                notifyDataSetChanged()
                statsListener(stats)
            }
        })
    }

    fun getAllBooks(): List<Book> {
        return dataSource.mapNotNull { it.book }
    }
}
