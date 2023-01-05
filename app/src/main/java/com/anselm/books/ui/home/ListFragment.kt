package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.BookViewModel
import com.anselm.books.BooksApplication
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Query
import com.anselm.books.databinding.FragmentListBinding
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class ListFragment: BookFragment() {
    protected val app = BooksApplication.app
    private var _binding: FragmentListBinding? = null
    protected val binding get() = _binding!!
    protected val bookViewModel: BookViewModel by viewModels { BookViewModel.Factory }
    private lateinit var adapter: BookAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentListBinding.inflate(inflater, container, false)

        bindAdapter()

        return binding.root
    }

    /**
     * Updates the query and let the repository knows.
     * This invalidates the underlying data source and optionally triggers a full rebinding of
     * the adapter - e.g. if you're changing the sort order.
     * This is overwritten by SearchFragment.
     */
    private var refreshJob: Job? = null
    protected open fun changeQuery(query: Query) {
        Log.d(TAG, "Change query to $query")
        if (refreshJob != null) {
            refreshJob?.cancel()
            refreshJob = null
        }
        bookViewModel.query = query
        bookViewModel.queryFlow.value = query
        adapter.submitData(lifecycle, PagingData.empty())
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bookViewModel.bookList.collectLatest { adapter.submitData(it) }
            }
        }
    }

    /**
     * Changes the sort order of the list and takes action to have the UI reflect the change.
     */
    protected fun changeSortOrder(sortOrder: Int) {
        changeQuery(bookViewModel.queryFlow.value.copy(sortBy = sortOrder))
    }

    /**
     * Binds a new adapter to the recycler.
     * Quite frankly this is meant to work around the weakness of recyclerview to handle
     * changing sort order. It tries to resync the pre/post lists and ends up in hell.
     */
    private fun bindAdapter() {
        // Creates the new adapter and restarts the jobs.
        adapter = BookAdapter { book -> adapterOnClick(book) }
        // Collects from the state and updates the progress bar accordingly.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collect {
                    app.loading(it.source.prepend is LoadState.Loading
                            || it.source.append is LoadState.Loading, "$TAG.recycler")
                }
            }
        }
        binding.list.adapter = adapter
        binding.list.layoutManager = LinearLayoutManager(binding.list.context)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun adapterOnClick(book: Book) {
        val repository = BooksApplication.app.repository
        viewLifecycleOwner.lifecycleScope.launch {
            val bookIds = repository.getIdsList(bookViewModel.query)
            val position = bookIds.indexOf(book.id)
            val action = HomeFragmentDirections.toPagerFragment(
                bookIds.toLongArray(), position
            )
            findNavController().navigate(action)
        }
    }

}

