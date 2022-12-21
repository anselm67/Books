package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.*
import com.anselm.books.databinding.FragmentListBinding
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class ListFragment: Fragment() {
    private val app = BooksApplication.app
    private var _binding: FragmentListBinding? = null
    protected val binding get() = _binding!!
    protected val viewModel: QueryViewModel by viewModels()

    /**
     * Is this the home screen or some other screen that inherit from it.
     * For now, only SearchFragment inherits frome HomeFragment.
     */
    private fun isHome() = (this !is SearchFragment)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentListBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val adapter = BookAdapter { book -> adapterOnClick(book) }
        binding.bindAdapter(bookAdapter = adapter)

        // If we're on the home screen, resets the query so we have the full list, otherwise
        // preserve any settings in the query viewModel.
        if ( isHome() ) {
            val query = Query()
            viewModel.query.value = query
            app.repository.query = query
        }

        val bookViewModel: BookViewModel by viewModels {
            BookViewModelFactory(app.repository)
        }

        // Collects from the Article Flow in the ViewModel, and submits to the adapter.
        viewLifecycleOwner.lifecycleScope.launch {
            // We repeat on the STARTED lifecycle because an Activity may be PAUSED
            // but still visible on the screen, for example in a multi window app
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bookViewModel.data.collectLatest {
                    Log.d(TAG, "Submitting data to the adapter.")
                    adapter.submitData(it)
                }
            }
        }
        // Collects from the state and updates the progress bar accordingly.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collect {
                    app.loading(it.source.prepend is LoadState.Loading
                            || it.source.append is LoadState.Loading)
                }
            }
        }

        binding.fab.setOnClickListener {
            scanISBN()
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun adapterOnClick(book: Book) {
        val action = HomeFragmentDirections.actionHomeFragmentToDetailsFragment(book.id)
        findNavController().navigate(action)
    }

    private fun scanISBN() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13)
            .build()
        val scanner = GmsBarcodeScanning.getClient(requireContext(), options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                // Task completed successfully
                Log.d(TAG, "Found ISBN $barcode.")
                if (barcode.valueType == Barcode.TYPE_ISBN && barcode.rawValue != null) {
                    handleISBN(barcode.rawValue!!)
                }
            }.addOnCanceledListener {
                // Task canceled
                Log.d(TAG, "Scanner canceled")
            }.addOnFailureListener { e ->
                // Task failed with an exception
                Log.e(TAG, "Scanner failed.", e)
                app.toast(getString(R.string.scan_failed, e.message))
            }
    }

    private fun handleISBN(isbn: String) {
        app.loading(true)
        app.olClient.lookup(isbn, { msg: String, e: Exception? ->
            app.loading(false)
            Log.e(TAG, "$isbn: ${msg}.", e)
            app.toast("No matches found for $isbn")
        }, {
            app.loading(false)
            val activity = requireActivity()
            view?.let { myself -> activity.hideKeyboard(myself) }
            requireActivity().lifecycleScope.launch(Dispatchers.Main) {
                val action = HomeFragmentDirections.actionEditNewBook(-1, it)
                findNavController().navigate(action)
            }
        })
    }

}

/**
 * Sets up the [RecyclerView] and binds [BookAdapter] to it
 */
private fun FragmentListBinding.bindAdapter(bookAdapter: BookAdapter) {
    list.adapter = bookAdapter
    list.layoutManager = LinearLayoutManager(list.context)
}
