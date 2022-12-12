package com.anselm.books.ui.home

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.*

class QueryViewModel : ViewModel() {
    var query: MutableLiveData<Query> = MutableLiveData<Query>()
}

class SearchFragment : ListFragment() {
    // Button's Drawable to use to open a filter dialog.
    private lateinit var filterDrawable: Drawable
    // Button's Drawable to use when a filter value is selected, to clear it out.
    private lateinit var clearFilterDrawable: Drawable

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

        // Displays filters in this view, that's the whole point.
        binding.idSearchFilters.isVisible = true
        handleMenu(requireActivity())

        // Caches the drawable for the filter buttons.
        filterDrawable = ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_baseline_arrow_drop_down_24)!!
        clearFilterDrawable = ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_baseline_clear_24)!!

        // We start with a fresh query, initialized with our arguments.
        if (viewModel.query.value == null) {
            viewModel.query.value = Query()
        }
        if (safeArgs.location != "") viewModel.query.value?.location = safeArgs.location
        if (safeArgs.genre != "") viewModel.query.value?.genre = safeArgs.genre
        if (safeArgs.publisher != "") viewModel.query.value?.publisher = safeArgs.publisher

        // Let's go.
        BooksApplication.app.repository.query = viewModel.query.value!!
        updateFiltersUi()

        viewModel.query.observe(viewLifecycleOwner) {
            BooksApplication.app.repository.query = viewModel.query.value!!
            updateFiltersUi()
        }

        return root
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

    private fun clearFilter(columnName: String) {
        var query: Query? = null
        when(columnName) {
            SearchDialogFragment.PHYSICAL_LOCATION ->
                query = viewModel.query.value?.copy(location = null)
            SearchDialogFragment.GENRE ->
                query = viewModel.query.value?.copy(genre = null)
            SearchDialogFragment.PUBLISHER ->
                query = viewModel.query.value?.copy(publisher = null)
        }
        query?.let { viewModel.query.value = it }
    }
    
    private fun dialogFilter(columnName: String) {
        view?.let { activity?.hideKeyboard(it) }
        val action = SearchFragmentDirections.actionSearchFragmentToSearchDialogFragment(columnName)
        findNavController().navigate(action)
    }

    data class Filter(
        val columnName: String,
        val button: Button,
        val value: String?,
        val label: Int)

    private fun updateFiltersUi() {
        val filters = arrayOf(
            Filter(SearchDialogFragment.PHYSICAL_LOCATION,
                binding.idLocationFilter,
                viewModel.query.value?.location,
                R.string.physicalLocationLabel),
            Filter(SearchDialogFragment.GENRE,
                binding.idGenreFilter,
                viewModel.query.value?.genre,
                R.string.genreLabel),
            Filter(SearchDialogFragment.PUBLISHER,
                binding.idPublisherFilter,
                viewModel.query.value?.publisher,
                R.string.publisherLabel))
        for (f in filters) {
            if (f.value!= null && f.value != "") {
                f.button.text = f.value
                f.button.typeface = Typeface.create(f.button.typeface, Typeface.BOLD)
                f.button.setOnClickListener { clearFilter(f.columnName) }
                f.button.setCompoundDrawablesWithIntrinsicBounds(
                    /* left, top, right, bottom */
                    null,  null, clearFilterDrawable, null)
            } else {
                f.button.text = getString(f.label)
                f.button.setOnClickListener { dialogFilter(f.columnName) }
                f.button.setCompoundDrawablesWithIntrinsicBounds(
                    /* left, top, right, bottom */
                    null, null, filterDrawable, null)
            }
        }
    }

    private fun bindSearch(menu: Menu) {
        menu.findItem(R.id.idEditBook)?.isVisible = false
        menu.findItem(R.id.idSaveBook)?.isVisible = false
        menu.findItem(R.id.idGotoSearchView)?.isVisible = false
        // Handles the search view:
        val item = menu.findItem(R.id.idSearchView)
        val query = viewModel.query.value?.copy()
        item.isVisible = true
        (item.actionView as SearchView).let {
            it.setQuery(query?.query, false)
            it.isIconified = false
            it.clearFocus()
            it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?): Boolean {
                    query?.query = text
                    query?.partial = false
                    viewModel.query.value = query!!
                    return false
                }
                override fun onQueryTextChange(text: String?): Boolean {
                    val emptyText = (text == null || text == "")
                    query?.query = if  (emptyText) null else text
                    query?.partial =  ! emptyText
                    viewModel.query.value = query
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


