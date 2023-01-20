package com.anselm.books.ui.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.Property
import com.anselm.books.R
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import com.anselm.books.databinding.BottomSheetMultiEditDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class EditMultiDialogFragment: BottomSheetDialogFragment() {
    private var _binding: BottomSheetMultiEditDialogBinding? = null
    private val binding get() = _binding!!
    private val editors = mutableListOf<Editor<*>>()
    private lateinit var bookIds: List<Long>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val safeArgs: EditMultiDialogFragmentArgs by navArgs()
        bookIds = safeArgs.bookIds.toList()
        _binding = BottomSheetMultiEditDialogBinding.inflate(inflater, container, false)
        binding.idHeader.text = getString(R.string.edit_multiple_books, bookIds.size)

        binding.idCancelDialog.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.idApplyButton.setOnClickListener {
            applyChanges()
        }
        bind(inflater)

        handleMenu(requireActivity())
        updateApplyButton()
        return binding.root
    }

    private fun isChanged(): Boolean {
        var changed = false
        editors.forEach {
            if (it.isChanged()) {
                changed = true
                it.saveChange()
            }
        }
        return changed
    }

    private fun applyChanges() {
        // No changes, good news.
        if ( ! isChanged() )
            return
        // Gets the work done, even when painful.
        app.loading(true)
        app.applicationScope.launch {
            bookIds.map { bookId ->
                val target = app.repository.load(bookId, true) ?: return@launch
                if (book.genres.isNotEmpty())
                    target.genres = book.genres
                if (book.authors.isNotEmpty())
                    target.authors = book.authors
                if (book.publisher != null)
                    target.publisher = book.publisher
                if (book.language != null)
                    target.language = book.language
                if (book.location != null)
                    target.location = book.location
                app.repository.save(target, false)
            }
            app.loading(false)
            app.postOnUiThread { findNavController().popBackStack() }
        }
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.forEach {
                    it.isVisible = false
                }
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }


    private fun updateApplyButton() {
        binding.idApplyButton.isEnabled = editors.firstOrNull {
            Property.isNotEmpty(it.getValue())
        } != null
    }

    // This is just a holder for the values we want to collect.
    private val book = Book()

    private fun bind(inflater: LayoutInflater) {
        editors.addAll(arrayListOf(
            MultiLabelEditor(this, inflater, book, {  updateApplyButton() },
                Label.Type.Authors, R.string.authorLabel,
                Book::authors),
            MultiLabelEditor(this, inflater, book, {  updateApplyButton() },
                Label.Type.Genres, R.string.genreLabel,
                Book::genres),
            SingleLabelEditor(this, inflater, book, {  updateApplyButton() },
                Label.Type.Publisher, R.string.publisherLabel,
                Book::publisher),
            SingleLabelEditor(this, inflater, book, {  updateApplyButton() },
                Label.Type.Language, R.string.languageLabel,
                Book::language),
            SingleLabelEditor(this, inflater, book, {  updateApplyButton() },
                Label.Type.Location, R.string.physicalLocationLabel,
                Book::location),
        ))
        editors.forEach {
            it.setup(binding.idEditorContainer)?.let {
                    view -> binding.idEditorContainer.addView(view)
            }
        }
    }

}