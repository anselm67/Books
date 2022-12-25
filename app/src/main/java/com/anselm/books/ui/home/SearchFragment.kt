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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
        super.onCreateView(inflater, container, savedInstanceState)
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
        if (safeArgs.query != null) viewModel.query.value?.query = safeArgs.query
        if (safeArgs.location != null) viewModel.query.value?.location = safeArgs.location
        if (safeArgs.genre != null) viewModel.query.value?.genre = safeArgs.genre
        if (safeArgs.publisher != null) viewModel.query.value?.publisher = safeArgs.publisher
        if (safeArgs.author != null) viewModel.query.value?.author = safeArgs.author
        viewModel.query.value?.sortBy = safeArgs.sortBy

        // Let's go.
        app.repository.query = viewModel.query.value!!
        updateFiltersUi()

        viewModel.query.observe(viewLifecycleOwner) {
            app.repository.query = viewModel.query.value!!
            updateFiltersUi()
        }

        app.repository.itemCount.observe(viewLifecycleOwner) {
            binding.idCountView.text = getString(R.string.item_count_format, it)
        }

        return root
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                bindSearch(menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.idSortByDateAdded -> {
                        val query = viewModel.query.value?.copy(sortBy = BookDao.SortByDateAdded)
                        viewModel.query.value = query
                        app.repository.query = query!!
                        bindAdapter()
                        true
                    }
                    R.id.idSortByTitle -> {
                        val query = viewModel.query.value?.copy(sortBy = BookDao.SortByTitle)
                        viewModel.query.value = query
                        app.repository.query = query!!
                        bindAdapter()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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
            SearchDialogFragment.AUTHOR ->
                query = viewModel.query.value?.copy(author = null)
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
                R.string.publisherLabel),
            Filter(SearchDialogFragment.AUTHOR,
                binding.idAuthorFilter,
                viewModel.query.value?.author,
                R.string.authorLabel))
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
        menu.findItem(R.id.idSortByDateAdded)?.isVisible = true
        menu.findItem(R.id.idSortByTitle)?.isVisible = true
        menu.findItem(R.id.idEditBook)?.isVisible = false
        menu.findItem(R.id.idSaveBook)?.isVisible = false
        menu.findItem(R.id.idGotoSearchView)?.isVisible = false
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
                    viewModel.query.value = viewModel.query.value?.copy(
                        query = text, partial = false)
                    return false
                }
                override fun onQueryTextChange(text: String?): Boolean {
                    val emptyText = (text == null || text == "")
                    viewModel.query.value = viewModel.query.value?.copy(
                        query = if  (emptyText) null else text,
                        partial = ! emptyText)
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
                    navBackStackEntry.savedStateHandle.get<Pair<String, String>>("filter")
                if (result != null) {
                    val (columnName, value) = result
                    Log.d(TAG, "Filter $columnName with $value")
                    when (columnName) {
                        SearchDialogFragment.PHYSICAL_LOCATION ->
                            viewModel.query.value = viewModel.query.value?.copy(location = value)
                        SearchDialogFragment.GENRE ->
                            viewModel.query.value = viewModel.query.value?.copy(genre = value)
                        SearchDialogFragment.PUBLISHER ->
                            viewModel.query.value = viewModel.query.value?.copy(publisher = value)
                        SearchDialogFragment.AUTHOR ->
                            viewModel.query.value = viewModel.query.value?.copy(author = value)
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


