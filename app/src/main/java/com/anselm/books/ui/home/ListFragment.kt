package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.*
import com.anselm.books.database.Book
import com.anselm.books.database.Query
import com.anselm.books.databinding.FragmentListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class ListFragment: Fragment() {
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

        changeQuery(bookViewModel.query)
        return binding.root
    }

    /**
     * Updates the query and let the repository knows.
     * This invalidates the underlying data source and optionally triggers a full rebinding of
     * the adapter - e.g. if you're changing the sort order.
     * This is overwritten by SearchFragment.
     */
    protected open fun changeQuery(query: Query) {
        Log.d(TAG, "Change query to $query")
        bookViewModel.query = query
        bookViewModel.queryFlow.value = query
        adapter.submitData(lifecycle, PagingData.empty())
        viewLifecycleOwner.lifecycleScope.launch {
            bookViewModel.bookList.collectLatest { adapter.submitData(it) }
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

    // FIXME This should somehow move to BooksApplication.
    private val allItemIds = arrayOf(
        R.id.idSortByDateAdded,
        R.id.idSortByTitle,
        R.id.idGotoSearchView,
        R.id.idEditBook,
        R.id.idSaveBook,
        R.id.idSearchView,
    )

    // For subclasses to finish any toolbar work.
    protected open fun onCreateMenu(menu: Menu) { }

    protected fun handleMenu(items: List<Pair<Int, () -> Unit>>) {
        requireActivity().addMenuProvider(object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Everything is invisible ...
                allItemIds.forEach { menu.findItem(it)?.isVisible = false}
                // Unless requested by the fragment.
                items.forEach {
                    menu.findItem(it.first)?.isVisible = true
                }
                onCreateMenu(menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val found = items.firstOrNull { menuItem.itemId == it.first }
                return if (found != null) {
                    found.second()
                    true
                } else {
                    false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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

