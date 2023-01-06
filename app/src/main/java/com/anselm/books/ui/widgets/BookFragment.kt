package com.anselm.books.ui.widgets

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.anselm.books.BooksApplication
import com.anselm.books.R

open class BookFragment: Fragment() {
    protected lateinit var app: BooksApplication

    private val allItemIds = arrayOf(
        R.id.idSortByDateAdded,
        R.id.idSortByTitle,
        R.id.idGotoSearchView,
        R.id.idEditBook,
        R.id.idSaveBook,
        R.id.idSearchView,
        R.id.idDeleteBook,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = BooksApplication.app
    }

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

}