package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
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
import com.anselm.books.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val adapter = BookAdapter { book -> adapterOnClick(book) }
        binding.bindAdapter(bookAdapter = adapter)

        val repository = (activity?.application as BooksApplication).repository
        val bookViewModel: BookViewModel by viewModels {
            BookViewModelFactory(repository)
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
                    binding.loadProgress.isVisible = it.source.prepend is LoadState.Loading
                            || it.source.append is LoadState.Loading
                }
            }
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                bindSearch(repository, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }

        })
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

private fun bindSearch(repository: BookRepository, menu: Menu) {
    val searchItem = menu.findItem(R.id.idSearchView)
    val searchView = searchItem.actionView as SearchView
    searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            repository.titleQuery = query
            return false
        }
        override fun onQueryTextChange(newText: String?) = false
    })
    searchView.setOnCloseListener {
        repository.titleQuery = null
        false
    }

}

/**
 * Sets up the [RecyclerView] and binds [BookAdapter] to it
 */
private fun FragmentHomeBinding.bindAdapter(bookAdapter: BookAdapter) {
    list.adapter = bookAdapter
    list.layoutManager = LinearLayoutManager(list.context)
}

