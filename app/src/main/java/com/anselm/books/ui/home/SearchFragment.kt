package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.hideKeyboard

class QueryViewModel : ViewModel() {
    var query = MutableLiveData("")
    var partialMatch = MutableLiveData(false)
    var location = MutableLiveData("")
    val genre = MutableLiveData("")
}

class SearchFragment : ListFragment() {
    private val viewModel: QueryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        val safeArgs: SearchFragmentArgs by navArgs()
        Log.d(TAG, "safeArgs query=${safeArgs.query}, " +
                "location=${safeArgs.location}, " +
                "genre=${safeArgs.genre}")

        binding.idSearchFilters.isVisible = true
        handleMenu(requireActivity())
        binding.idLocationFilter.setOnClickListener {
            locationFilter()
        }

        viewModel.query.observe(viewLifecycleOwner) { runQuery() }
        viewModel.location.observe(viewLifecycleOwner) { runQuery() }
        viewModel.genre.observe(viewLifecycleOwner) { runQuery() }

        return root
    }

    override fun onStop() {
        super.onStop()
        viewModel.query.value = null
        viewModel.location.value = null
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                bindSearch(menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        })
    }

    private fun locationFilter() {
        view?.let { activity?.hideKeyboard(it) }
        val action = SearchFragmentDirections.actionSearchFragmentToSearchDialogFragment()
        findNavController().navigate(action)
    }

    private fun runQuery() {
        Log.d(TAG, "runQuery ${viewModel.query.value}/${viewModel.partialMatch.value}," +
                " location: ${viewModel.location.value}," +
                " genre: ${viewModel.genre.value}")
        BooksApplication.app.repository.titleQuery =
            if (viewModel.partialMatch.value == true) viewModel.query.value+'*' else viewModel.query.value
        BooksApplication.app.repository.physicalLocation = viewModel.location.value
    }

    private fun bindSearch(menu: Menu) {
        menu.findItem(R.id.idEditBook)?.isVisible = false
        menu.findItem(R.id.idSaveBook)?.isVisible = false
        menu.findItem(R.id.idGotoSearchView)?.isVisible = false
        // Handles the search view:
        val item = menu.findItem(R.id.idSearchView)
        item.isVisible = true
        (item.actionView as SearchView).let {
            it.setQuery(viewModel.query.value, false)
            it.isIconified = false
            it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    viewModel.query.value = query
                    viewModel.partialMatch.value = false
                    return false
                }
                override fun onQueryTextChange(newText: String?): Boolean {
                    val emptyText = (newText == null || newText == "")
                    viewModel.query.value = if  (emptyText) null else newText
                    viewModel.partialMatch.value =  ! emptyText
                    return true
                }
            })
            it.setOnCloseListener {
                this.findNavController().popBackStack()
                false
            }
        }
    }

}


