package com.anselm.books.ui.edit

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.NumberPicker.OnValueChangeListener
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
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
import com.anselm.books.databinding.EditFieldLayoutBinding
import com.anselm.books.databinding.EditYearLayoutBinding
import com.anselm.books.databinding.FragmentEditBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class EditFragment: Fragment() {
    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!
    private var book: Book? = null

    private var validBorder: Drawable? = null
    private var invalidBorder: Drawable? = null
    private var changedBorder: Drawable? = null

    private var editors: List<Editor>? = null

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
        val root: View = binding.root

        val repository = (activity?.application as BooksApplication).repository
        val safeArgs: EditFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            book = repository.getBook(safeArgs.bookId)
            book?.let { editors = bind(inflater, it) }
        }

        // Caches the borders corresponding to the various states of individual field editors.
        validBorder = getBorderDrawable(R.drawable.textview_border)
        invalidBorder = getBorderDrawable(R.drawable.textview_border_invalid)
        changedBorder = getBorderDrawable(R.drawable.textview_border_changed)

        handleMenu(requireActivity())

        return root
    }

    fun setInvalidBorder(view: View) {
        view.background = invalidBorder
    }

    fun setChangedBorder(view: View) {
        view.background = changedBorder
    }

    fun setRegularBorder(view: View) {
        view.background = validBorder
    }

    /**
     * Sets the given [editText] border to color code a pending changes.
     * Returns true if the value has been edited, false otherwise.
     */
    fun setBorder(editText: EditText, currentValue: String?): Boolean  {
        val newValue = editText.text.toString().trim()
        return if (newValue == currentValue) {
            setRegularBorder(editText)
            false
        } else {
            setChangedBorder(editText)
            true
        }
    }

    private fun bind(inflater: LayoutInflater, book: Book): List<Editor> {
        val app = BooksApplication.app
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
        // Creates and sets up an editor for every book property.
        val fields = arrayListOf(
            TextEditor(this, inflater, R.string.titleLabel, book::title.getter, book::title.setter),
            TextEditor(this, inflater, R.string.subtitleLabel, book::subtitle.getter, book::subtitle.setter),
            TextEditor(this, inflater, R.string.authorLabel, book::author.getter, book::author.setter),
            TextEditor(this, inflater, R.string.summaryLabel, book::summary.getter, book::summary.setter),
            TextEditor(this, inflater, R.string.isbnLabel, book::isbn.getter, book::isbn.setter) {
                isValidEAN13(it)
            },
            YearEditor(this, inflater, book::yearPublished.getter, book::yearPublished.setter))
        fields.forEach {
            binding.editView.addView(it.setup(binding.editView))
        }
        return fields
    }

    private fun saveChanges() {
        var changed = false
        editors?.forEach { changed = changed || it.isChanged() }
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

    private fun digit(c: Char): Int {
        return c.digitToInt()
    }

    private fun isValidEAN13(isbn: String): Boolean {
        if (isbn.length != 13) {
            return false
        }
        // Computes the expected checksum / last digit.
        val sum1 = arrayListOf(0, 2, 4, 6, 8, 10).sumOf { it -> digit(isbn[it]) }
        val sum2 = 3 * arrayListOf(1, 3, 5, 7, 9, 11).sumOf { it -> digit(isbn[it]) }
        val checksum = (sum1 + sum2) % 10
        val expected = if (checksum == 0) '0' else ('0' + 10 - checksum)
        return expected == isbn[12]
    }
}

private abstract class Editor(
    open val fragment: EditFragment,
    open val inflater: LayoutInflater) {
    abstract fun setup(container: ViewGroup?): View
    abstract fun isChanged(): Boolean
    abstract fun saveChange()
}

private class YearEditor(
    override val fragment: EditFragment,
    override val inflater: LayoutInflater,
    val getter: () -> String,
    val setter: (String) -> Unit
): Editor(fragment, inflater) {
    private var _binding: EditYearLayoutBinding? = null
    private val editor get() = _binding!!

    private fun getEditorValue(): Int {
        return (editor.yearPublished100Picker.value * 100
                + editor.yearPublished10Picker.value * 10
                + editor.yearPublished1Picker.value)
    }

    private fun setEditorValue(value: Int) {
        if (value in Book.MIN_PUBLISHED_YEAR..Book.MAX_PUBLISHED_YEAR) {
            editor.yearPublished100Picker.value = value / 100
            editor.yearPublished10Picker.value = (value / 100) % 10
            editor.yearPublished1Picker.value = value % 10
        }
    }

    override fun setup(container: ViewGroup?): View {
        _binding = EditYearLayoutBinding.inflate(inflater, container, false)
        editor.yearPublished100Picker.minValue = Book.MIN_PUBLISHED_YEAR / 100
        editor.yearPublished100Picker.maxValue = Book.MAX_PUBLISHED_YEAR / 100
        editor.yearPublished10Picker.minValue = 0
        editor.yearPublished10Picker.maxValue = 9
        editor.yearPublished1Picker.minValue = 0
        editor.yearPublished1Picker.maxValue = 9
        setEditorValue(getter().toIntOrNull() ?: 0)
        val onValueChanged = OnValueChangeListener { _, _, _ ->
            val newValue = getEditorValue()
            if (newValue != getter().toIntOrNull()) {
                fragment.setChangedBorder(editor.yearPublishedView)
            } else {
                fragment.setRegularBorder(editor.yearPublishedView)
            }
        }
        editor.yearPublished100Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished10Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished1Picker.setOnValueChangedListener(onValueChanged)
        return editor.root
    }

    override fun isChanged(): Boolean {
        val value = getEditorValue()
        return (getter().toIntOrNull() ?: 0) == value
    }

    override fun saveChange() {
        val value = getEditorValue()
        setter(value.toString())
    }
}

private class TextEditor(
    override val fragment: EditFragment,
    override val inflater: LayoutInflater,
    val labelId: Int,
    val getter: () -> String,
    val setter: (String) -> Unit,
    val checker: ((String) -> Boolean)? = null
): Editor(fragment, inflater) {
    private var _binding: EditFieldLayoutBinding? = null
    private val editor get() = _binding!!

    override fun setup(container: ViewGroup?): View {
        _binding = EditFieldLayoutBinding.inflate(inflater, container, false)
        editor.idEditLabel.text = fragment.getText(labelId)
        editor.idEditText.let {
            it.setText(getter())
            it.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

                override fun afterTextChanged(s: Editable?) {
                    val value = s.toString().trim()
                    if ( checker != null && ! checker.invoke(value) ) {
                        fragment.setInvalidBorder(it)
                        editor.idUndoEdit.setColorFilter(ContextCompat.getColor(
                            fragment.requireContext(), R.color.editorValueInvalid))
                    } else if (fragment.setBorder(it, getter()) ) {
                        editor.idUndoEdit.setColorFilter(ContextCompat.getColor(
                            fragment.requireContext(), R.color.editorValueChanged))
                    } else {
                        editor.idUndoEdit.setColorFilter(ContextCompat.getColor(
                            fragment.requireContext(), R.color.editorValueUnchanged))
                    }
                }
            })
        }
        editor.idUndoEdit.setOnClickListener {
            editor.idEditText.setText(getter())
        }
        return editor.root
    }

    override fun isChanged(): Boolean {
        val value = editor.idEditText.text.toString().trim()
        return value != getter()
    }

    override fun saveChange() {
        setter(editor.idEditText.text.toString().trim())
    }

}