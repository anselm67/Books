package com.anselm.books.ui.edit

import android.app.AlertDialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.BooksApplication
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import com.anselm.books.databinding.FragmentEditBinding
import com.anselm.books.ui.widgets.BookFragment
import com.anselm.books.ui.widgets.MenuItemHandler
import kotlinx.coroutines.launch
import java.lang.Integer.max


class EditFragment: BookFragment() {
    private var _binding: FragmentEditBinding? = null
    private lateinit var book: Book
    val binding get() = _binding!!

    private var validBorder: Drawable? = null
    private var invalidBorder: Drawable? = null
    private var changedBorder: Drawable? = null

    private var editors: MutableList<Editor> = emptyList<Editor>().toMutableList()
    private val coverImageEditor
        get() = (editors[0] as CoverImageEditor)

    private val editorStatusListener = StatusListener(this)

    private fun getBorderDrawable(resourceId: Int): Drawable {
        return ResourcesCompat.getDrawable(resources, resourceId, null)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        val repository = app.repository

        // We restart the list of editors from scratch, cause we might get restarted.
        editors = mutableListOf(CoverImageEditor(this, inflater, editorStatusListener) {
            app.imageRepository.getCoverUri(book)
        })

        // Parses the arguments, we can have either:
        // - A bookId, which means we want to edit/update an existing book,
        // - A Book instance which is to be created/ inserted.
        val safeArgs: EditFragmentArgs by navArgs()
        if (safeArgs.bookId > 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                book = repository.load(safeArgs.bookId, decorate = true)!!
                bind(inflater, book)
            }
        } else if (safeArgs.book != null) {
            book = safeArgs.book!!
            bind(inflater, book)
        } else {
            Log.d(TAG, "No books to edit.")
        }

        // Caches the borders corresponding to the various states of individual field editors.
        validBorder = getBorderDrawable(R.drawable.textview_border)
        invalidBorder = getBorderDrawable(R.drawable.textview_border_invalid)
        changedBorder = getBorderDrawable(R.drawable.textview_border_changed)

        handleMenu(
            MenuItemHandler(R.id.idSaveBook, {
                checkChanges()
            }),
            MenuItemHandler(R.id.idDeleteBook, {
                val builder = AlertDialog.Builder(requireActivity())
                builder.setMessage(getString(R.string.delete_book_confirmation, book.title))
                    .setPositiveButton(R.string.yes) { _, _ -> deleteBook() }
                    .setNegativeButton(R.string.no) { _, _ -> }
                    .show()
            }),
        )
        return binding.root
    }

    private fun deleteBook() {
        val app = BooksApplication.app
        if (book.id >= 0) {
            app.applicationScope.launch {
                app.repository.deleteBook(book)
                app.toast(getString(R.string.book_deleted, book.title))
            }
        }
        findNavController().popBackStack()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    fun setChanged(editor: View, undoButton: ImageButton) {
        editor.background = changedBorder
        undoButton.visibility = VISIBLE
        undoButton.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.editorValueChanged)
        )
    }

    fun setInvalid(editor: View, undoButton: ImageButton) {
        editor.background = invalidBorder
        undoButton.visibility = VISIBLE
        undoButton.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.editorValueInvalid)
        )
    }

    fun setUnchanged(editor: View, undoButton: ImageButton) {
        editor.background = validBorder
        undoButton.visibility = GONE
        undoButton.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.editorValueUnchanged)
        )
    }

    fun scrollTo(view: View) {
        // Adds a tad so that the editor's label comes to view.
        binding.idScrollView.smoothScrollTo(0, max(0, view.top - 25))
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
            TextEditor(this, inflater, editorStatusListener, R.string.titleLabel,
                book::title.getter, book::title.setter) {
                it.isNotEmpty()
            },
            TextEditor(this, inflater, editorStatusListener, R.string.subtitleLabel,
                book::subtitle.getter, book::subtitle.setter),
            MultiLabelEditor(this, inflater, editorStatusListener,
                Label.Type.Authors, R.string.authorLabel,
                book::authors.getter, book::authors.setter),
            SingleLabelEditor(this, inflater, editorStatusListener,
                Label.Type.Publisher, R.string.publisherLabel,
                book::publisher.getter, book::publisher.setter),
            MultiLabelEditor(this, inflater, editorStatusListener,
                Label.Type.Genres, R.string.genreLabel,
                book::genres.getter, book::genres.setter),
            SingleLabelEditor(this, inflater, editorStatusListener,
                Label.Type.Location, R.string.physicalLocationLabel,
                book::location.getter, book::location.setter),
            TextEditor(this, inflater, editorStatusListener, R.string.isbnLabel,
                book::isbn.getter, book::isbn.setter) {
                isValidEAN13(it)
            },
            SingleLabelEditor(this, inflater, editorStatusListener,
                Label.Type.Language, R.string.languageLabel,
                book::language.getter, book::language.setter),
            TextEditor(this, inflater, editorStatusListener, R.string.numberOfPagesLabel,
                book::numberOfPages.getter, book::numberOfPages.setter) {
                isValidNumber(it)
            },
            TextEditor(this, inflater, editorStatusListener, R.string.summaryLabel,
                book::summary.getter, book::summary.setter),
            YearEditor(this, inflater, editorStatusListener,
                book::yearPublished.getter, book::yearPublished.setter),
        ))
        editors.forEach {
            it.setup(binding.editView)?.let { view -> binding.editView.addView(view) }
        }
    }

    private fun saveEditorChanges(): Boolean {
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
        if (saveEditorChanges() || book.id <= 0) {
            checkForDuplicates()
        } else {
            // Nothing to save, head back.
            findNavController().popBackStack()
        }
    }

    // Checks for duplicates and save the book if the user is ok.
    private fun checkForDuplicates() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dupes = app.repository.getDuplicates(book).filter {
                it.id != book.id
            }
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
            findNavController().popBackStack()
        }
    }

    private fun digit(c: Char): Int {
        return c.digitToInt()
    }

    private fun isValidEAN13(isbn: String): Boolean {
        // Quick checks: empty is fine.
        if (isbn.isEmpty()) {
            return true
        } else if (isbn.length != 13) {
            return false
        }
        // Computes the expected checksum / last digit.
        val sum1 = arrayListOf(0, 2, 4, 6, 8, 10).sumOf { it -> digit(isbn[it]) }
        val sum2 = 3 * arrayListOf(1, 3, 5, 7, 9, 11).sumOf { it -> digit(isbn[it]) }
        val checksum = (sum1 + sum2) % 10
        val expected = if (checksum == 0) '0' else ('0' + 10 - checksum)
        return expected == isbn[12]
    }

    private fun isValidNumber(number: String): Boolean {
        return number.firstOrNull {
            ! it.isDigit()
        } == null
    }
}

class StatusListener(private val delegate: EditFragment): EditorStatusListener() {
    override fun setChanged(container: View, undoButton: ImageButton) {
        delegate.setChanged(container, undoButton)
    }
    override fun setUnchanged(container: View, undoButton: ImageButton) {
        delegate.setUnchanged(container, undoButton)
    }
    override fun setInvalid(container: View, undoButton: ImageButton) =
        delegate.setInvalid(container, undoButton)

    override fun scrollTo(view: View) {
        delegate.scrollTo(view)
    }
    override fun checkCameraPermission(): Boolean {
        return delegate.checkCameraPermission()
    }
}

