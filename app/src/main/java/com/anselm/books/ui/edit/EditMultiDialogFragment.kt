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
import com.anselm.books.R
import com.anselm.books.database.Label
import com.anselm.books.databinding.BottomSheetMultiEditDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class EditMultiDialogFragment: BottomSheetDialogFragment() {
    private var _binding: BottomSheetMultiEditDialogBinding? = null
    private val binding get() = _binding!!
    private val editors = mutableListOf<Editor>()
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
                val book = app.repository.load(bookId, true) ?: return@launch
                if (genres.isNotEmpty())
                    book.genres = genres
                if (authors.isNotEmpty())
                    book.authors = authors
                if (publisher != null)
                    book.publisher = publisher
                if (language != null)
                    book.language = language
                if (location != null)
                    book.location = location
                app.repository.save(book, false)
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


    var authors: List<Label> = mutableListOf()
    var genres: List<Label> = mutableListOf()
    var language: Label? = null
    var publisher: Label? =null
    var location: Label? =null

    private fun bind(inflater: LayoutInflater) {
        editors.addAll(arrayListOf(
            MultiLabelEditor(this, inflater, null,
                Label.Type.Authors, R.string.authorLabel,
                this::authors.getter, this::authors.setter),
            MultiLabelEditor(this, inflater, null,
                Label.Type.Genres, R.string.genreLabel,
                this::genres.getter, this::genres.setter),
            SingleLabelEditor(this, inflater, null,
                Label.Type.Publisher, R.string.publisherLabel,
                this::publisher.getter, this::publisher.setter),
            SingleLabelEditor(this, inflater, null,
                Label.Type.Language, R.string.languageLabel,
                this::language.getter, this::language.setter),
            SingleLabelEditor(this, inflater, null,
                Label.Type.Location, R.string.physicalLocationLabel,
                this::location.getter, this::location.setter),
        ))
        editors.forEach {
            it.setup(binding.idEditorContainer)?.let {
                    view -> binding.idEditorContainer.addView(view)
            }
        }
    }

}