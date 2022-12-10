package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.*
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
import kotlinx.coroutines.launch

open class ListFragment: Fragment() {
    private var _binding: FragmentListBinding? = null
    protected val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val adapter = BookAdapter { book -> adapterOnClick(book) }
        binding.bindAdapter(bookAdapter = adapter)

        // Resets the query before creating the paging source.
        val app = BooksApplication.app
        app.repository.query = Query()
        val bookViewModel: BookViewModel by viewModels {
            BookViewModelFactory(app.repository)
        }

        // Collect from the Article Flow in the ViewModel, and submit it to the
        // ListAdapter.
        viewLifecycleOwner.lifecycleScope.launch {
            // We repeat on the STARTED lifecycle because an Activity may be PAUSED
            // but still visible on the screen, for example in a multi window app
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bookViewModel.data.collect {
                    Log.d(TAG, "Submitting data to the adapter.")
                    adapter.submitData(it)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collect {
                    app.loading(it.source.prepend is LoadState.Loading
                            || it.source.append is LoadState.Loading)
                }
            }
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
}

/**
 * Sets up the [RecyclerView] and binds [BookAdapter] to it
 */
private fun FragmentListBinding.bindAdapter(bookAdapter: BookAdapter) {
    list.adapter = bookAdapter
    list.layoutManager = LinearLayoutManager(list.context)
}
