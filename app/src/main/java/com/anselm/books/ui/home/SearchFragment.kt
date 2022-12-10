package com.anselm.books.ui.home

import android.graphics.Typeface
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
import com.anselm.books.*

class QueryViewModel : ViewModel() {
    var query: MutableLiveData<Query> = MutableLiveData<Query>()
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

        viewModel.query.value = BooksApplication.app.repository.query
        viewModel.query.observe(viewLifecycleOwner) {
            runQuery()
            updateLocation()
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

    private fun locationFilter() {
        view?.let { activity?.hideKeyboard(it) }
        val action = SearchFragmentDirections.actionSearchFragmentToSearchDialogFragment()
        findNavController().navigate(action)
    }

    private fun updateLocation() {
        val location = viewModel.query.value?.location
        if (location != null && location != "") {
            binding.idLocationFilter.let {
                it.text = location
                it.typeface = Typeface.create(it.typeface, Typeface.BOLD)
            }
        }
    }

    private fun runQuery() {
        viewModel.query.value?.let {
            BooksApplication.app.repository.query = viewModel.query.value!!
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


