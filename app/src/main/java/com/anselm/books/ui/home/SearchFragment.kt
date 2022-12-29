package com.anselm.books.ui.home

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.MutableLiveData

import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.*
import com.anselm.books.database.BookDao
import com.anselm.books.database.Query
import kotlinx.coroutines.launch

class QueryViewModel : ViewModel() {
    var query: MutableLiveData<Query> = MutableLiveData<Query>()
    var pagingSource: BookPagingSource? = null
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
        Log.d(TAG, "SearchFragment query : ${viewModel.query.value}")
        Log.d(TAG, "safeArgs query=${safeArgs.query}, " +
                "location=${safeArgs.location}, " +
                "genre=${safeArgs.genre}, " +
                "publisher=${safeArgs.publisher}, " +
                "author=${safeArgs.author}")

        // Displays filters in this view, that's the whole point.
        binding.idSearchFilters.isVisible = true
        binding.idCountView.isVisible = true
        binding.fab.isVisible = false

        // Customizes the toolbar menu.
        handleMenu(listOf(
            Pair(R.id.idSortByDateAdded) {
                changeSortOrder(BookDao.SortByDateAdded)
            },
            Pair(R.id.idSortByTitle) {
                changeSortOrder(BookDao.SortByTitle)
            }
        ))

        // Caches the drawable for the filter buttons.
        filterDrawable = ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_baseline_arrow_drop_down_24)!!
        clearFilterDrawable = ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_baseline_clear_24)!!

        // We start with a fresh query, initialized with our arguments.
        if (viewModel.query.value == null) {
            viewModel.query.value = Query()
        }
        viewModel.query.value?.query = safeArgs.query
        viewModel.query.value?.location = safeArgs.location
        viewModel.query.value?.genre = safeArgs.genre
        viewModel.query.value?.publisher = safeArgs.publisher
        viewModel.query.value?.author = safeArgs.author
        viewModel.query.value?.sortBy = safeArgs.sortBy

        // Let's go.
        app.repository.query = viewModel.query.value!!
        updateFiltersUi()

        app.repository.itemCount.observe(viewLifecycleOwner) {
            binding.idCountView.text = getString(R.string.item_count_format, it)
            // The count is updated once query processing is finished.
            // It's a good time to scroll back up.
            binding.list.scrollToPosition(0)
        }
        return root
    }

    override fun changeQuery(query: Query?, rebind: Boolean) {
        super.changeQuery(query, rebind)
        updateFiltersUi()
    }

    override fun onCreateMenu(menu: Menu) {
        bindSearch(menu)
    }

    private fun clearFilter(columnName: String) {
        var query: Query? = null
        when(columnName) {
            SearchDialogFragment.PHYSICAL_LOCATION ->
                query = viewModel.query.value?.copy(location = 0L)
            SearchDialogFragment.GENRE ->
                query = viewModel.query.value?.copy(genre = 0L)
            SearchDialogFragment.PUBLISHER ->
                query = viewModel.query.value?.copy(publisher = 0L)
            SearchDialogFragment.AUTHOR ->
                query = viewModel.query.value?.copy(author = 0L)
        }
        query?.let { changeQuery(it) }
    }
    
    private fun dialogFilter(columnName: String) {
        view?.let { activity?.hideKeyboard(it) }
        val action = SearchFragmentDirections.actionSearchFragmentToSearchDialogFragment(columnName)
        findNavController().navigate(action)
    }

    data class Filter(
        val columnName: String,
        val button: Button,
        val value: Long,
        val label: Int,
    )

    private fun updateFiltersUi() {
        val filters = arrayOf(
            Filter(SearchDialogFragment.PHYSICAL_LOCATION,
                binding.idLocationFilter,
                viewModel.query.value?.location?: 0L,
                R.string.physicalLocationLabel),
            Filter(SearchDialogFragment.GENRE,
                binding.idGenreFilter,
                viewModel.query.value?.genre?: 0L,
                R.string.genreLabel),
            Filter(SearchDialogFragment.PUBLISHER,
                binding.idPublisherFilter,
                viewModel.query.value?.publisher?: 0L,
                R.string.publisherLabel),
            Filter(SearchDialogFragment.AUTHOR,
                binding.idAuthorFilter,
                viewModel.query.value?.author?: 0L,
                R.string.authorLabel))
        val repository = BooksApplication.app.repository
        viewLifecycleOwner.lifecycleScope.launch {
            for (f in filters) {
                if (f.value != 0L) {
                    f.button.text = repository.label(f.value).name
                    f.button.typeface = Typeface.create(f.button.typeface, Typeface.BOLD)
                    f.button.setOnClickListener { clearFilter(f.columnName) }
                    f.button.setCompoundDrawablesWithIntrinsicBounds(
                        /* left, top, right, bottom */
                        null, null, clearFilterDrawable, null
                    )
                } else {
                    f.button.text = getString(f.label)
                    f.button.setOnClickListener { dialogFilter(f.columnName) }
                    f.button.setCompoundDrawablesWithIntrinsicBounds(
                        /* left, top, right, bottom */
                        null, null, filterDrawable, null
                    )
                }
            }
        }
    }

    private fun bindSearch(menu: Menu) {
        // Handles the search view:
        val item = menu.findItem(R.id.idSearchView)
        item.isVisible = true

        // Expands the menu item.
        // As this pushes an event to the backstack, we need to pop it automatically so that
        // when back is pressed it backs out to HomeFragment rather than just collapsing
        // the SearchView.
        item.expandActionView()
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean  = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                findNavController().popBackStack()
                return true
            }
        })

        // Customizes the search view's action view.
        (item.actionView as SearchView).let {
            it.setQuery(viewModel.query.value?.query, false)
            it.isIconified = false
            it.clearFocus()
            it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?): Boolean {
                    changeQuery(viewModel.query.value?.copy(
                        query = text, partial = false))
                    return false
                }
                override fun onQueryTextChange(text: String?): Boolean {
                    val emptyText = (text == null || text == "")
                    changeQuery(viewModel.query.value?.copy(
                        query = if  (emptyText) null else text,
                        partial = ! emptyText))
                    return true
                }
            })
            it.setOnCloseListener {
                this.findNavController().popBackStack()
                false
            }
        }
    }

    /**
     * Collects and sets up the return value from our filter dialog.
     * This is largely inspired by this link:
     * https://developer.android.com/guide/navigation/navigation-programmatic
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        val navBackStackEntry = navController.getBackStackEntry(R.id.nav_search)

        // Create our observer and add it to the NavBackStackEntry's lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME
                && navBackStackEntry.savedStateHandle.contains("filter")) {
                val result =
                    navBackStackEntry.savedStateHandle.get<Pair<String, Long>>("filter")
                if (result != null) {
                    val (columnName, value) = result
                    Log.d(TAG, "Filter $columnName with $value")
                    when (columnName) {
                        SearchDialogFragment.PHYSICAL_LOCATION ->
                            changeQuery(viewModel.query.value?.copy(location = value))
                        SearchDialogFragment.GENRE ->
                            changeQuery(viewModel.query.value?.copy(genre = value))
                        SearchDialogFragment.PUBLISHER ->
                            changeQuery(viewModel.query.value?.copy(publisher = value))
                        SearchDialogFragment.AUTHOR ->
                            changeQuery(viewModel.query.value?.copy(author = value))
                    }
                }
            }
        }
        navBackStackEntry.lifecycle.addObserver(observer)

        // As addObserver() does not automatically remove the observer, we
        // call removeObserver() manually when the view lifecycle is destroyed
        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                navBackStackEntry.lifecycle.removeObserver(observer)
            }
        })
    }
}


