package com.anselm.books.ui.edit

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.View.OnLayoutChangeListener
import android.widget.EditText
import android.widget.NumberPicker.OnValueChangeListener
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.*
import com.anselm.books.databinding.EditFieldLayoutBinding
import com.anselm.books.databinding.EditYearLayoutBinding
import com.anselm.books.databinding.FragmentEditBinding
import com.anselm.books.ui.home.SearchDialogFragment
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

        if (safeArgs.bookId > 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                book = repository.getBook(safeArgs.bookId)
                book?.let { editors = bind(inflater, it) }
            }
        } else if (safeArgs.book != null) {
            book = safeArgs.book
            editors = bind(inflater, book!!)
        } else {
            Log.d(TAG, "No books to edit.")
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
        val uri = app.getCoverUri(book)
        // Binds the cover to its image via Glide.
        if (uri != null) {
            Glide.with(app.applicationContext)
                .load(uri).centerCrop()
                .placeholder(R.mipmap.ic_book_cover)
                .into(binding.coverImageView)
        } else {
            Glide.with(app.applicationContext)
                .load(R.mipmap.ic_book_cover)
                .into(binding.coverImageView)
        }
        // Creates and sets up an editor for every book property.
        val fields = arrayListOf(
            TextEditor(this, inflater, R.string.titleLabel,
                book::title.getter, book::title.setter),
            TextEditor(this, inflater, R.string.subtitleLabel,
                book::subtitle.getter, book::subtitle.setter),
            TextEditor(this, inflater, R.string.authorLabel,
                book::author.getter, book::author.setter),
            SearchDialogEditor(this, inflater,
                SearchDialogFragment.PUBLISHER,
                R.string.publisherLabel,
                book::publisher.getter, book::publisher.setter),
            SearchDialogEditor(this, inflater,
                SearchDialogFragment.GENRE,
                R.string.genreLabel,
                book::genre.getter, book::genre.setter),
            SearchDialogEditor(this, inflater,
                SearchDialogFragment.PHYSICAL_LOCATION,
                R.string.physicalLocationLabel,
                book::physicalLocation.getter, book::physicalLocation.setter),
            TextEditor(this, inflater, R.string.isbnLabel,
                book::isbn.getter, book::isbn.setter) {
                isValidEAN13(it)
            },
            TextEditor(this, inflater, R.string.languageLabel,
                book::language.getter, book::language.setter),
            TextEditor(this, inflater, R.string.numberOfPagesLabel,
                book::numberOfPages.getter, book::numberOfPages.setter) {
                isValidNumber(it)
            },
            TextEditor(this, inflater, R.string.summaryLabel,
                book::summary.getter, book::summary.setter),
            YearEditor(this, inflater, book::yearPublished.getter, book::yearPublished.setter))
        fields.forEach {
            binding.editView.addView(it.setup(binding.editView))
        }
        return fields
    }

    private fun saveChanges() {
        val app = BooksApplication.app
        if (book != null && book!!.id <= 0) {
            // We're inserting a new book into the library.
            activity?.lifecycleScope?.launch {
                app.database.bookDao().insert(book!!)
                app.toast("${book?.title} added.")
            }
        } else {
            var changed = false
            editors?.forEach {
                if (it.isChanged()) {
                    changed = true
                    it.saveChange()
                }
            }
            if (changed) {
                activity?.lifecycleScope?.launch {
                    app.database.bookDao().update(book!!)
                    app.toast("${book?.title} updated.")
                }
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

    private fun isValidNumber(number: String): Boolean {
        return number.firstOrNull {
            ! it.isDigit()
        } == null
    }

    private fun getSearchDialogEditorByColumnName(columnName: String): SearchDialogEditor {
        return (editors?.firstOrNull {
            (it is SearchDialogEditor) && it.columnName == columnName
        } as SearchDialogEditor)
    }

    /**
     * Collects and sets up the return value from our filter dialog.
     * This is largely inspired by this link:
     * https://developer.android.com/guide/navigation/navigation-programmatic
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        val navBackStackEntry = navController.getBackStackEntry(R.id.nav_edit)

        // Create our observer and add it to the NavBackStackEntry's lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME
                && navBackStackEntry.savedStateHandle.contains("filter")) {
                val result = navBackStackEntry.savedStateHandle.get<Pair<String, String>>("filter")
                if (result != null) {
                    val (columnName, value) = result
                    getSearchDialogEditorByColumnName(columnName).
                        setEditorValue(value)
                }
            }
        }
        navBackStackEntry.lifecycle.addObserver(observer)

        // As addObserver() does not automatically remove the observer, we
        // call removeObserver() manually when the view lifecycle is destroyed
        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                navBackStackEntry.lifecycle.removeObserver(observer)
            }
        })
    }

}

private abstract class Editor(
    open val fragment: EditFragment,
    open val inflater: LayoutInflater) {
    abstract fun setup(container: ViewGroup?): View
    abstract fun isChanged(): Boolean
    abstract fun saveChange()

    abstract fun setEditorValue(value: String)
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

    fun setEditorValue(value: Int) {
        if (value in BookFields.MIN_PUBLISHED_YEAR..BookFields.MAX_PUBLISHED_YEAR) {
            editor.yearPublished100Picker.value = value / 100
            editor.yearPublished10Picker.value = (value / 100) % 10
            editor.yearPublished1Picker.value = value % 10
        }
    }

    override fun setEditorValue(value: String) {
        setEditorValue(value.toIntOrNull() ?: 0)
    }

    override fun setup(container: ViewGroup?): View {
        _binding = EditYearLayoutBinding.inflate(inflater, container, false)
        editor.yearPublished100Picker.minValue = BookFields.MIN_PUBLISHED_YEAR / 100
        editor.yearPublished100Picker.maxValue = BookFields.MAX_PUBLISHED_YEAR / 100
        editor.yearPublished10Picker.minValue = 0
        editor.yearPublished10Picker.maxValue = 9
        editor.yearPublished1Picker.minValue = 0
        editor.yearPublished1Picker.maxValue = 9
        setEditorValue(getter().toIntOrNull() ?: 0)
        val onValueChanged = OnValueChangeListener { _, _, _ ->
            val newValue = getEditorValue()
            if (newValue != getter().toIntOrNull()) {
                fragment.setChangedBorder(editor.yearPublishedView)
                editor.idUndoEdit.setColorFilter(ContextCompat.getColor(
                    fragment.requireContext(), R.color.editorValueChanged))
            } else {
                fragment.setRegularBorder(editor.yearPublishedView)
                editor.idUndoEdit.setColorFilter(ContextCompat.getColor(
                    fragment.requireContext(), R.color.editorValueUnchanged))
            }
        }
        editor.yearPublished100Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished10Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished1Picker.setOnValueChangedListener(onValueChanged)
        editor.idUndoEdit.setOnClickListener {
            setEditorValue(getter().toIntOrNull() ?: 0)
            fragment.setRegularBorder(editor.yearPublishedView)
            editor.idUndoEdit.setColorFilter(ContextCompat.getColor(
                fragment.requireContext(), R.color.editorValueUnchanged))
        }

        return editor.root
    }

    override fun isChanged(): Boolean {
        val value = getEditorValue()
        return (getter().toIntOrNull() ?: 0) != value
    }

    override fun saveChange() {
        val value = getEditorValue()
        setter(value.toString())
    }
}

private open class TextEditor(
    override val fragment: EditFragment,
    override val inflater: LayoutInflater,
    open val labelId: Int,
    open val getter: () -> String,
    open val setter: (String) -> Unit,
    open val checker: ((String) -> Boolean)? = null
): Editor(fragment, inflater) {
    private var _binding: EditFieldLayoutBinding? = null
    protected val editor get() = _binding!!

    override fun setEditorValue(value: String) {
        editor.idEditText.setText(value)
    }

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
                    if (checker != null && ! checker!!.invoke(value)) {
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
            // Sets up a layout listener to enable scrolling on this EditText.
            setupScrollEnableListener(it)
        }
        editor.idUndoEdit.setOnClickListener {
            editor.idEditText.setText(getter())
        }
        return editor.root
    }

    private fun setupScrollEnableListener(editText: EditText) {
        editText.addOnLayoutChangeListener(object: OnLayoutChangeListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onLayoutChange(
                v: View?,left: Int,top: Int,right: Int,
                bottom: Int,oldLeft: Int,oldTop: Int,oldRight: Int,oldBottom: Int) {
                val layoutLines = editText.layout?.lineCount ?: 0
                if (layoutLines  > editText.maxLines) {
                    // I have no idea what this does, I haven't read the docs. Sigh.
                    // What I can say is that it allows to scroll the EditText widget even
                    // though it is itself in a scrollable view:
                    // ScrollView > LinearLayout > Editor (EditText)
                    editText.setOnTouchListener { view, event ->
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        return@setOnTouchListener false
                    }
                }
            }
        })
    }

    override fun isChanged(): Boolean {
        val value = editor.idEditText.text.toString().trim()
        return value != getter()
    }

    override fun saveChange() {
        setter(editor.idEditText.text.toString().trim())
    }

}

private class SearchDialogEditor(
    override val fragment: EditFragment,
    override val inflater: LayoutInflater,
    val columnName: String,
    override val labelId: Int,
    override val getter: () -> String,
    override val setter: (String) -> Unit
): TextEditor(fragment, inflater, labelId, getter, setter) {

    override fun setup(container: ViewGroup?): View {
        val root = super.setup(container)
        editor.idEditLabel.setOnClickListener {
            val action = EditFragmentDirections.actionEditFragmentToSearchDialogFragment(
                columnName)
            fragment.findNavController().navigate(action)
        }
        return root
    }
}