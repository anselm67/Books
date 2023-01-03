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
import androidx.lifecycle.lifecycleScope

import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.*
import com.anselm.books.database.BookDao
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import kotlinx.coroutines.launch

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
        Log.d(TAG, "safeArgs query=${safeArgs.query}")

        // Displays filters in this view, that's the whole point.
        binding.idSearchFilters.isVisible = true
        binding.idCountView.isVisible = true
        binding.fab.isVisible = false

        // Caches the drawable for the filter buttons.
        filterDrawable = ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_baseline_arrow_drop_down_24)!!
        clearFilterDrawable = ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_baseline_clear_24)!!

        // Customizes the toolbar menu.
        handleMenu(listOf(
            Pair(R.id.idSortByDateAdded) {
                changeSortOrder(BookDao.SortByDateAdded)
            },
            Pair(R.id.idSortByTitle) {
                changeSortOrder(BookDao.SortByTitle)
            }
        ))

        // We start with a fresh query, initialized with our arguments.
        if (safeArgs.query != null) {
            changeQuery(safeArgs.query!!)
        } else {
            changeQuery(bookViewModel.query)
        }

        // Let's go.
        refreshUi()

        return root
    }

    fun changeQueryAndUpdateUI(query: Query) {
        super.changeQuery(query)
        refreshUi()
    }

    override fun onCreateMenu(menu: Menu) {
        bindSearch(menu)
    }

    private fun clearFilter(type: Label.Type) {
        val query = bookViewModel.query.copy()
        query.clearFilter(type)
        changeQueryAndUpdateUI(query)
    }
    
    private fun dialogFilter(type: Label.Type) {
        view?.let { activity?.hideKeyboard(it) }
        val action = SearchFragmentDirections.actionSearchFragmentToSearchDialogFragment(
            type, bookViewModel.query)
        findNavController().navigate(action)
    }

    data class Filter(
        val type: Label.Type,
        val button: Button,
        val filter: Query.Filter?,
        val label: Int,
    )

    private fun refreshUi() {
        // Refreshes the filters state.
        val filters = arrayOf(
            Filter(Label.Type.Location,
                binding.idLocationFilter,
                bookViewModel.query.firstFilter(Label.Type.Location),
                R.string.physicalLocationLabel),
            Filter(Label.Type.Genres,
                binding.idGenreFilter,
                bookViewModel.query.firstFilter(Label.Type.Genres),
                R.string.genreLabel),
            Filter(Label.Type.Publisher,
                binding.idPublisherFilter,
                bookViewModel.query.firstFilter(Label.Type.Publisher),
                R.string.publisherLabel),
            Filter(Label.Type.Authors,
                binding.idAuthorFilter,
                bookViewModel.query.firstFilter(Label.Type.Authors),
                R.string.authorLabel))
        val repository = BooksApplication.app.repository
        viewLifecycleOwner.lifecycleScope.launch {
            for (f in filters) {
                if (f.filter != null) {
                    f.button.text = repository.label(f.filter.labelId).name
                    f.button.typeface = Typeface.create(f.button.typeface, Typeface.BOLD)
                    f.button.setOnClickListener { clearFilter(f.type) }
                    f.button.setCompoundDrawablesWithIntrinsicBounds(
                        /* left, top, right, bottom */
                        null, null, clearFilterDrawable, null
                    )
                } else {
                    f.button.text = getString(f.label)
                    f.button.setOnClickListener { dialogFilter(f.type) }
                    f.button.setCompoundDrawablesWithIntrinsicBounds(
                        /* left, top, right, bottom */
                        null, null, filterDrawable, null
                    )
                }
            }
            // Refreshes the book count.
            val count = repository.getPagedListCount(bookViewModel.query)
            binding.idCountView.text = getString(R.string.item_count_format, count)
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
            it.setQuery(bookViewModel.query.query, false)
            it.isIconified = false
            it.clearFocus()
            it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?): Boolean {
                    changeQueryAndUpdateUI(bookViewModel.query.copy(
                        query = text, partial = false))
                    return false
                }
                override fun onQueryTextChange(text: String?): Boolean {
                    val emptyText = (text == null || text == "")
                    changeQueryAndUpdateUI(bookViewModel.query.copy(
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
                    navBackStackEntry.savedStateHandle.get<Query.Filter>("filter")
                if (result != null) {
                    Log.d(TAG, "Setting filter $result")
                    val query = bookViewModel.query.copy()
                    query.setOrReplace(result)
                    changeQueryAndUpdateUI(query)
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


