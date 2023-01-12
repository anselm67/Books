package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.BookDao
import com.anselm.books.database.Query
import com.anselm.books.databinding.BottomAddDialogBinding
import com.anselm.books.hideKeyboard
import com.anselm.books.ui.widgets.MenuItemHandler
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : ListFragment() {
    private var totalCount: Int = 0

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        binding.idSearchFilters.isVisible = false
        binding.idCountView.isVisible = false
        binding.fabScanButton.isVisible = true
        binding.fabEditButton.isVisible = false

        // Handles the menu items we care about.
        handleMenu(listOf(
            MenuItemHandler(R.id.idGotoSearchView, {
                val action = HomeFragmentDirections.toSearchFragment(
                    Query(sortBy = bookViewModel.query.sortBy)
                )
                findNavController().navigate(action)
            }),
            MenuItemHandler(R.id.idSortByDateAdded, {
                changeSortOrder(BookDao.SortByDateAdded)
            }),
            MenuItemHandler(R.id.idSortByTitle, {
                changeSortOrder(BookDao.SortByTitle)
            })
        ))

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                totalCount = app.repository.getTotalCount()
                app.title = getString(R.string.book_count, totalCount)
            }
        }
        binding.fabScanButton.setOnClickListener {
            showBottomAddDialog()
        }


        changeQuery(bookViewModel.query)

        return root
    }

    override fun onSelectionChanged(selectedCount: Int) {
        super.onSelectionChanged(selectedCount)
        if (selectedCount > 0) {
            app.title = getString(R.string.book_selected_count, selectedCount)
        } else {
            app.title = getString(R.string.book_count, totalCount)
        }
    }

    private fun showBottomAddDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val binding = BottomAddDialogBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)

        binding.idScan.setOnClickListener {
            val action = HomeFragmentDirections.toScanFragment()
            findNavController().navigate(action)
            dialog.dismiss()
        }
        binding.idType.setOnClickListener {
            val action = HomeFragmentDirections.toEditFragment(-1, app.repository.newBook())
            findNavController().navigate(action)
            dialog.dismiss()
        }
        binding.idIsbn.setOnClickListener {
            binding.idIsbnEdit.visibility =
                if (binding.idIsbnEdit.visibility != View.VISIBLE) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            binding.idIsbnButton.visibility = binding.idIsbnEdit.visibility
            dialog.show()
        }
        binding.idIsbnButton.setOnClickListener{
            val isbn = binding.idIsbnEdit.text.toString().trim()
            view?.let { myself -> activity?.hideKeyboard(myself) }
            handleISBN(isbn)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun handleISBN(isbn: String) {
        app.loading(true, "$TAG.handleISBM")
        app.lookup(isbn, { msg: String, e: Exception? ->
            Log.e(TAG, "$isbn: ${msg}.", e)
            app.toast("No matches found for $isbn")
            app.loading(false, "$TAG.handleISBM")
        }, { book ->
            if (book == null) {
                app.toast("Book not found.")
            } else {
                val activity = requireActivity()
                view?.let { myself -> activity.hideKeyboard(myself) }
                activity.lifecycleScope.launch(Dispatchers.Main) {
                    val action = HomeFragmentDirections.toEditFragment(-1, book)
                    findNavController().navigate(action)
                }
            }
            app.loading(false, "$TAG.handleISBM")
        })
    }
}

