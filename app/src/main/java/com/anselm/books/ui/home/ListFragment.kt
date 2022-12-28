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
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.*
import com.anselm.books.database.Book
import com.anselm.books.databinding.FragmentListBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class ListFragment: Fragment() {
    protected val app = BooksApplication.app
    private var _binding: FragmentListBinding? = null
    protected val binding get() = _binding!!
    protected val viewModel: QueryViewModel by viewModels()
    private lateinit var bookViewModel: BookViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentListBinding.inflate(inflater, container, false)
        val root: View = binding.root

        bindAdapter()

        val _bookViewModel: BookViewModel by viewModels {
            BookViewModelFactory(app.repository)
        }
        bookViewModel = _bookViewModel

        return root
    }

    private var dataCollector: Job? = null
    private var stateCollector: Job? = null
    private var adapter: BookAdapter? = null

    protected fun bindAdapter() {
        // Cancels any jobs we have running with the previous adapter.
        dataCollector?.cancel()
        stateCollector?.cancel()

        // Creates the new adapter and restarts the jobs.
        val newAdapter = BookAdapter { book -> adapterOnClick(book) }
        binding.list.adapter = newAdapter
        binding.list.layoutManager = LinearLayoutManager(binding.list.context)

        // Collects from the Article Flow in the ViewModel, and submits to the adapter.
        dataCollector = viewLifecycleOwner.lifecycleScope.launch {
            // We repeat on the STARTED lifecycle because an Activity may be PAUSED
            // but still visible on the screen, for example in a multi window app
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bookViewModel.data.collectLatest {
                    Log.d(TAG, "Submitting data to the adapter.")
                    newAdapter.submitData(it)
                }
            }
        }
        // Collects from the state and updates the progress bar accordingly.
        stateCollector = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                newAdapter.loadStateFlow.collect {
                    app.loading(it.source.prepend is LoadState.Loading
                            || it.source.append is LoadState.Loading, "$TAG.recycler")
                }
            }
        }

        // Swaps the adapter.
        if (adapter != null) {
            binding.list.swapAdapter(newAdapter, true)
        } else {
            binding.list.adapter = newAdapter
        }
        adapter = newAdapter
    }

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

