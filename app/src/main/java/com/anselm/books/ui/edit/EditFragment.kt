package com.anselm.books.ui.edit

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.Book
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentEditBinding
import com.anselm.books.ui.details.DetailsFragmentArgs
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class EditFragment: Fragment() {
    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!
    private var book: Book? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val repository = (activity?.application as BooksApplication).repository
        val safeArgs: DetailsFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            book = repository.getBook(safeArgs.bookId)
            book?.let { bind(it) }
        }

        handleMenu(requireActivity())
        return root
    }

    private fun bind(book: Book) {
        val app = BooksApplication.app
        // Binds all the simple text fields.
        binding.titleView.setText(book.title)
        binding.subtitleView.setText(book.subtitle)
        binding.authorView.setText(book.author)
        binding.summaryView.setText(book.summary)
        // Binds the year published number picker.
        binding.yearPublished100Picker.minValue = 0
        binding.yearPublished100Picker.maxValue = 20
        binding.yearPublished10Picker.minValue = 0
        binding.yearPublished10Picker.maxValue = 9
        binding.yearPublished1Picker.minValue = 0
        binding.yearPublished1Picker.maxValue = 9
        val yearPublished = book.yearPublished.toIntOrNull()
        if (yearPublished != null && yearPublished != 0) {
            binding.yearPublished100Picker.value = yearPublished / 100
            binding.yearPublished10Picker.value = (yearPublished / 100) % 10
            binding.yearPublished1Picker.value = yearPublished % 10
        } else {
            binding.yearPublished100Picker.value = 0
            binding.yearPublished10Picker.value = 0
            binding.yearPublished1Picker.value = 0
        }

        // Binds the cover to its image via Glide.
        if (book.imageFilename != "") {
            Glide.with(app.applicationContext)
                .load(app.getCoverUri(book.imageFilename)).centerCrop()
                .placeholder(R.mipmap.ic_book_cover)
                .into(binding.coverImageView)
        } else {
            Glide.with(app.applicationContext)
                .load(R.mipmap.ic_book_cover)
                .into(binding.coverImageView)
        }
    }

    private fun updateTitle(): Boolean {
        val value = binding.titleView.text.toString()
        if (value != book?.title) {
            book?.title = value
            return true
        }
        return false
    }

    private fun updateSubtitle(): Boolean {
        val value = binding.subtitleView.text.toString()
        if (value != book?.subtitle) {
            book?.subtitle = value
            return true
        }
        return false
    }

    private fun updateAuthor(): Boolean {
        val value = binding.authorView.text.toString()
        if (value != book?.author) {
            book?.author = value
            return true
        }
        return false
    }

    private fun updateSummary(): Boolean {
        val value = binding.summaryView.text.toString()
        if (value != book?.summary) {
            book?.summary = value
            return true
        }
        return false
    }

    private fun updateYearPublished(): Boolean {
        val value: Int = (binding.yearPublished100Picker.value * 100
                + binding.yearPublished10Picker.value * 10
                + binding.yearPublished1Picker.value)
        if (value != 0 && value != book?.yearPublished?.toIntOrNull()) {
            book?.yearPublished = value.toString()
            return true
        }
        return false
    }

    private fun saveChanges() {
        if (book == null) {
            return
        }
        val changed = (updateTitle()
                || updateSubtitle()
                || updateAuthor()
                || updateSummary()
                || updateYearPublished())
        if (changed) {
            activity?.lifecycleScope?.launch {
                val app = BooksApplication.app
                app.database.bookDao().update(book!!)
                app.toast("${book?.title} updated.")
            }
        }
        findNavController().popBackStack()
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.findItem(R.id.idSearchView)?.isVisible = false
                menu.findItem(R.id.idEditBook)?.isVisible = false
                menu.findItem(R.id.idSaveBook)?.isVisible = true
                menu.findItem(R.id.idGotoSearchView)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.idSaveBook) {
                    Log.d(TAG, "Saving book ${this@EditFragment.book?.id}.")
                    saveChanges()
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}

