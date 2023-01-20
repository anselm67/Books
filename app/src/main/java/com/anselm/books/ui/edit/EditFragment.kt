package com.anselm.books.ui.edit

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.BooksApplication
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.R
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import com.anselm.books.databinding.FragmentEditBinding
import com.anselm.books.ui.widgets.BookFragment
import com.anselm.books.ui.widgets.MenuItemHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class EditFragment: BookFragment() {
    private var _binding: FragmentEditBinding? = null
    private lateinit var book: Book
    val binding get() = _binding!!

    private var editors: MutableList<Editor> = emptyList<Editor>().toMutableList()
    private val coverImageEditor
        get() = (editors[0] as CoverImageEditor)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object: OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if ( this@EditFragment.isChanged() || book.id <= 0) {
                        AlertDialog.Builder(requireActivity())
                            .setMessage(getString(R.string.discard_changes_prompt))
                            .setPositiveButton(R.string.yes) { _, _ ->
                                isEnabled = false
                                requireActivity().onBackPressedDispatcher.onBackPressed()
                            }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .show()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            })
    }

    /**
     * The order in which stuff is done matters a lot here: launchers can't be created after
     * any views is initialized, so we init them first thing, and conclude with the binding of the
     * book's rendering, which creates all the views.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentEditBinding.inflate(inflater, container, false)

        // Parses the arguments, we can have either:
        // - A bookId, which means we want to edit/update an existing book,
        // - A Book instance which is to be created/ inserted.
        val safeArgs: EditFragmentArgs by navArgs()
        if (safeArgs.bookId > 0) {
            runBlocking {
                book = app.repository.load(safeArgs.bookId, decorate = true)!!
            }
        } else if (safeArgs.book != null) {
            book = safeArgs.book!!
        } else {
            app.toast(getString(R.string.edit_no_book_error))
            findNavController().popBackStack()
        }

        // Handles our menu item.
        handleMenu(
            MenuItemHandler(R.id.idDeleteBook, {
                AlertDialog.Builder(requireActivity())
                    .setMessage(getString(R.string.delete_book_confirmation, book.title))
                    .setPositiveButton(R.string.yes) { _, _ -> deleteBook() }
                    .setNegativeButton(R.string.no) { _, _ -> }
                    .show()
            }),
        )

        // Binds our fab button for save and magic.
        binding.fabSaveButton.setOnClickListener {
            checkChanges()
        }
        binding.fabMagicButton.setOnClickListener {
            performMagic()
        }

        editors = mutableListOf(CoverImageEditor(this@EditFragment, inflater, book))
        bind(inflater, book)
        return binding.root
    }

    private fun deleteBook() {
        val app = BooksApplication.app
        if (book.id >= 0) {
            app.applicationScope.launch {
                app.repository.deleteBook(book)
                app.toast(getString(R.string.book_deleted, book.title))
            }
        } else {
            book.status = Book.Status.Deleted
        }
        findNavController().popBackStack()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    /**
     * Creates and binds all field editors; Adds them all to the [editors] list.
     * Keep in mind that at this point, editors already contains the CoverImageEditor which
     * has to be initialized before any view is created. Cause it wants to
     * registerForActivityResult.
     */
    private fun bind(inflater: LayoutInflater, book: Book) {
        // Creates and sets up an editor for every book property.
        editors.addAll(arrayListOf(
            TextEditor(this, inflater, book, R.string.titleLabel,
                Book::title.getter, Book::title.setter) {
                it.isNotEmpty()
            },
            TextEditor(this, inflater, book, R.string.subtitleLabel,
                Book::subtitle.getter, Book::subtitle.setter),
            MultiLabelEditor(this, inflater, book,
                Label.Type.Authors, R.string.authorLabel,
                Book::authors.getter, Book::authors.setter),
            SingleLabelEditor(this, inflater, book,
                Label.Type.Publisher, R.string.publisherLabel,
                Book::publisher.getter, Book::publisher.setter),
            MultiLabelEditor(this, inflater, book,
                Label.Type.Genres, R.string.genreLabel,
                Book::genres.getter, Book::genres.setter),
            SingleLabelEditor(this, inflater, book,
                Label.Type.Location, R.string.physicalLocationLabel,
                Book::location.getter, Book::location.setter),
            TextEditor(this, inflater, book, R.string.isbnLabel,
                Book::isbn.getter, Book::isbn.setter, ISBN::isValidEAN13),
            SingleLabelEditor(this, inflater, book,
                Label.Type.Language, R.string.languageLabel,
                Book::language.getter, Book::language.setter),
            TextEditor(this, inflater, book, R.string.numberOfPagesLabel,
                Book::numberOfPages.getter, Book::numberOfPages.setter) {
                isValidNumber(it)
            },
            TextEditor(this, inflater, book, R.string.summaryLabel,
                Book::summary.getter, Book::summary.setter),
            YearEditor(this, inflater, book,
                Book::yearPublished.getter, Book::yearPublished.setter),
        ))
        editors.forEach {
            it.setup(binding.editView)?.let { view -> binding.editView.addView(view) }
        }
    }

    private fun isChanged(): Boolean {
        return editors.firstOrNull { it.isChanged() } != null
    }

    private fun saveChanges(): Boolean {
        var changed = false
        editors.forEach {
            if (it.isChanged()) {
                changed = true
                it.saveChange()
            }
        }
        return changed
    }

    private fun validateChanges():Boolean {
        return editors.firstOrNull { ! it.isValid() } == null
    }

    /**
     * Check for changes, validates them and saves the book being edited if all ok.
     * This is a three steps trip:
     * 1. checkChanges checks that there are changes, and that the changes are valid,
     * 2. checkForDuplicates check the edited book for duplicates and prompts the user as needed.
     * 3. If all above steps agree, doSave() actually writes the book and its image to the database.
     * */
    private fun checkChanges() {
        val app = BooksApplication.app
        // Validates the edits first, reject invalid books.
        if (!validateChanges()) {
            app.toast("Adjust highlighted fields.")
            return
        }
        // Inserts or saves only when valid.
        if (saveChanges() || book.id <= 0) {
            checkForDuplicates()
        } else {
            // Nothing to save, head back.
            findNavController().popBackStack()
        }
    }

    // Checks for duplicates and save the book if the user is ok.
    private fun checkForDuplicates() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dupes = app.repository.getDuplicates(book)
            if (dupes.isNotEmpty()) {
                val builder = AlertDialog.Builder(requireActivity())
                builder.setMessage(
                    getString(
                        R.string.duplicate_book_confirmation,
                        book.title,
                        dupes.size
                    )
                )
                .setPositiveButton(R.string.yes) { _, _ -> doSave() }
                .setNegativeButton(R.string.no) { _, _ -> }
                .show()
            } else {
                doSave()
            }
        }
    }

    // Saves the book no matter what, this is the last hop in the path to save the book being
    // edited: after saveChanges has checked that there are changes, and after checkForDuplicates
    // approved.
    private fun doSave() {
        app.loading(true)
        activity?.lifecycleScope?.launch {
            // saveCoverImage makes additional changes to the book, it needs to come first.
            coverImageEditor.saveCoverImage(book)
            app.repository.save(book)
        }?.invokeOnCompletion {
            app.toast("${book.title} saved.")
            app.loading(false)
            // When the save is very long, we might already have gone.
            if (isAdded ) {
                findNavController().popBackStack()
            }
        }
    }

    private fun isValidNumber(number: String): Boolean {
        return number.firstOrNull {
            ! it.isDigit()
        } == null
    }

    private fun mergeFrom(match: Book) {
        editors.forEach {
            it.extractValue(match)
        }
    }

    private fun performMagic() {
        if (book.isbn.isEmpty()) {
            app.toast("I need an ISBN number.")
            return
        }
        app.loading(true, "performMagic")
        app.lookupService.lookup(book.isbn, stopAt = null) {
            app.loading(false, "performMagic")
            if (it == null) {
                app.toast("No match found, no magic!")
            } else {
                mergeFrom(it)
            }
        }
    }
}

