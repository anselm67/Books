package com.anselm.books.ui.home

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.navigation.fragment.findNavController
import com.anselm.books.BookRepository
import com.anselm.books.BooksApplication
import com.anselm.books.R

class SearchFragment : ListFragment(){

    override fun onCreate(savedInstanceState: Bundle?) {
        val root = super.onCreate(savedInstanceState)
        handleMenu(requireActivity())
        return root
    }

    override fun onStop() {
        super.onStop()
        BooksApplication.app.repository.titleQuery = ""
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                bindSearch(BooksApplication.app.repository, menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        })
    }
}

private fun SearchFragment.bindSearch(repository: BookRepository, menu: Menu) {
    menu.findItem(R.id.idEditBook)?.isVisible = false
    menu.findItem(R.id.idSaveBook)?.isVisible = false
    menu.findItem(R.id.idGotoSearchView)?.isVisible = false
    // Handles the search view:
    val item = menu.findItem(R.id.idSearchView)
    item.isVisible = true
    (item.actionView as SearchView).let {
        it.isIconified = false
        it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                repository.titleQuery = query
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText == null || newText == "") {
                    repository.titleQuery = null
                } else {
                    repository.titleQuery = newText + '*'
                }
                return true
            }
        })
        it.setOnCloseListener {
            repository.titleQuery = null
            this.findNavController().popBackStack()
            false
        }
    }
}

