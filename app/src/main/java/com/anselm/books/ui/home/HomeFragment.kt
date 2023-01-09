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
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.BookDao
import com.anselm.books.database.Query
import com.anselm.books.databinding.BottomAddDialogBinding
import com.anselm.books.hideKeyboard
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : ListFragment() {
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        binding.idSearchFilters.isVisible = false
        binding.idCountView.isVisible = false
        binding.fab.isVisible = true

        // Handles the menu items we care about.
        handleMenu(listOf(
            Pair(R.id.idGotoSearchView) {
                val action = HomeFragmentDirections.toSearchFragment(
                    Query(sortBy = bookViewModel.query.sortBy)
                )
                findNavController().navigate(action)
            },
            Pair(R.id.idSortByDateAdded) {
                changeSortOrder(BookDao.SortByDateAdded)
            },
            Pair(R.id.idSortByTitle) {
                changeSortOrder(BookDao.SortByTitle)
            }
        ))

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.title = getString(R.string.book_count, app.repository.getTotalCount())
            }
        }

        binding.fab.setOnClickListener {
            showBottomAddDialog()
        }

        changeQuery(bookViewModel.query)

        return root
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

