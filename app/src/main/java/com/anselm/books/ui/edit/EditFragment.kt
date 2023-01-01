package com.anselm.books.ui.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.View.OnLayoutChangeListener
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Filter
import android.widget.ImageButton
import android.widget.NumberPicker.OnValueChangeListener
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.*
import com.anselm.books.database.Book
import com.anselm.books.database.BookFields
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import com.anselm.books.databinding.AutocompleteLabelLayoutBinding
import com.anselm.books.databinding.EditFieldLayoutBinding
import com.anselm.books.databinding.EditMultiLabelLayoutBinding
import com.anselm.books.databinding.EditYearLayoutBinding
import com.anselm.books.databinding.FragmentEditBinding
import com.anselm.books.ui.widgets.DnDList
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


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

        val repository = BooksApplication.app.repository


        // Parses the arguments, we can have either:
        // - A bookId, which means we want to edit/update an existing book,
        // - A Book instance which is to be created/ inserted.
        val safeArgs: EditFragmentArgs by navArgs()
        if (safeArgs.bookId > 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                book = repository.load(safeArgs.bookId, decorate = true)
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


    fun setChanged(editor: View, undoButton: ImageButton) {
        editor.background = changedBorder
        undoButton.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.editorValueChanged)
        )
    }

    fun setInvalid(editor: View, undoButton: ImageButton) {
        editor.background = invalidBorder
        undoButton.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.editorValueInvalid)
        )
    }

    fun setUnchanged(editor: View, undoButton: ImageButton) {
        editor.background = validBorder
        undoButton.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.editorValueUnchanged)
        )
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
            LabelEditor(this, inflater,
                Label.Type.Authors, R.string.authorLabel, singleValue = false,
                book::authors.getter, book::authors.setter),
            LabelEditor(this, inflater,
                Label.Type.Publisher, R.string.publisherLabel, singleValue = true,
                book::publishers.getter, book::publishers.setter),
            LabelEditor(this, inflater,
                Label.Type.Genres, R.string.genreLabel, singleValue = false,
                book::genres.getter, book::genres.setter),
            LabelEditor(this, inflater,
                Label.Type.Location, R.string.physicalLocationLabel, singleValue = true,
                book::locations.getter, book::locations.setter),
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

    private fun saveEditorChanges(): Boolean {
        var changed = false
        editors?.forEach {
            if (it.isChanged()) {
                changed = true
                it.saveChange()
            }
        }
        return changed
    }

    private fun saveChanges() {
        val app = BooksApplication.app
        check(book != null)
        val theBook = book!!
        if (theBook.id <= 0 || saveEditorChanges()) {
            activity?.lifecycleScope?.launch {
                app.repository.save(theBook)
            }
            app.toast("${theBook.title} saved.")
        }
        findNavController().popBackStack()
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.findItem(R.id.idSortByDateAdded)?.isVisible = false
                menu.findItem(R.id.idSortByTitle)?.isVisible = false
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

    fun setEditorValue(value: Int) {
        if (value in BookFields.MIN_PUBLISHED_YEAR..BookFields.MAX_PUBLISHED_YEAR) {
            editor.yearPublished100Picker.value = value / 100
            editor.yearPublished10Picker.value = (value / 100) % 10
            editor.yearPublished1Picker.value = value % 10
        }
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
                fragment.setChanged(editor.yearPublishedView, editor.idUndoEdit)
            } else {
                fragment.setUnchanged(editor.yearPublishedView, editor.idUndoEdit)
            }
        }
        editor.yearPublished100Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished10Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished1Picker.setOnValueChangedListener(onValueChanged)
        editor.idUndoEdit.setOnClickListener {
            setEditorValue(getter().toIntOrNull() ?: 0)
            fragment.setUnchanged(editor.yearPublishedView, editor.idUndoEdit)
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
                        fragment.setInvalid(it, editor.idUndoEdit)
                    } else if (value != getter() ) {
                        fragment.setChanged(it, editor.idUndoEdit)
                    } else {
                        fragment.setUnchanged(it, editor.idUndoEdit)
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

private class LabelArrayAdapter(
    val context: Activity,
    val type: Label.Type,
    val labels: List<Label>,
): ArrayAdapter<Label>(context, 0, labels) {
    lateinit var binding: AutocompleteLabelLayoutBinding

    override fun getView(position: Int, converterView: View?, parent: ViewGroup): View {
        var view = converterView
        if (view == null) {
            binding = AutocompleteLabelLayoutBinding.inflate(
                LayoutInflater.from(context), parent, false
            )
            view = binding.root
        }
        val label = getItem(position)
        if (label != null) {
            view.findViewById<TextView>(R.id.autoCompleteText).text = label.name
        }
        return view
    }

    inner class LabelFilter: Filter() {

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val repository = BooksApplication.app.repository
            val labelQuery = if (constraint?.isNotEmpty() == true) "$constraint*" else ""
            val results = FilterResults()
            runBlocking {
                val histos = BooksApplication.app.repository.getHisto(
                    type,
                    labelQuery,
                    Query()
                )
                results.count = histos.size
                results.values = histos.map { repository.label(it.labelId) }
                Log.d(TAG, "Returning ${histos.size} results.")
            }
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            clear()
            if (results != null ) {
                addAll(results.values as List<Label>)
            }
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return (resultValue as Label).name
        }
    }

    override fun getFilter(): Filter {
        return LabelFilter()
    }
}

private class LabelEditor(
    override val fragment: EditFragment,
    override val inflater: LayoutInflater,
    val type: Label.Type,
    val labelId: Int,
    val singleValue: Boolean,
    val getter: () -> List<Label>,
    val setter: (List<Label>) -> Unit
) : Editor (fragment, inflater) {
    private var _binding: EditMultiLabelLayoutBinding? = null
    private val editor get() = _binding!!
    private lateinit var dndlist: DnDList

    override fun setup(container: ViewGroup?): View {
        _binding = EditMultiLabelLayoutBinding.inflate(inflater, container, false)
        editor.idEditLabel.text = fragment.getText(labelId)
        editor.labels.layoutManager = LinearLayoutManager(editor.labels.context)

        val repository = BooksApplication.app.repository
        // Sets up te drag and drop list view for displaying the existing labels.
        dndlist = DnDList(
            editor.labels,
            getter().toMutableList(),
            onChange = { newLabels ->
                if (newLabels != getter()) {
                    fragment.setChanged(editor.labels, editor.idUndoEdit)
                } else {
                    fragment.setUnchanged(editor.labels, editor.idUndoEdit)
                }
            }
        )

        // Sets up the auto-complete for entering new labels.
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val labels = repository.getHisto(type).map {
                repository.label(it.labelId)
            }
            val adapter = LabelArrayAdapter(
                fragment.requireActivity(),
                type,
                labels,
            )
            editor.autoComplete.threshold = 1
            editor.autoComplete.setAdapter(adapter)
            editor.autoComplete.setOnItemClickListener { parent, _, position, _ ->
                addLabel(parent.getItemAtPosition(position) as Label)
            }
        }
        editor.autoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.lastOrNull() != '\n') return
                // Process this by appending the corresponding label to the list.
                runBlocking {
                    addLabel(repository.label(type, input.trim()))
                }
            }
        })

        // Sets up the undo button.
        editor.idUndoEdit.setOnClickListener {
            fragment.setUnchanged(editor.labels, editor.idUndoEdit)
            dndlist.setLabels(getter().toMutableList())
        }
        return editor.root
    }

    override fun isChanged(): Boolean {
        return dndlist.getLabels() != getter()
    }

    override fun saveChange() {
        setter(dndlist.getLabels())
    }

    fun addLabel(label: Label) {
        val changed = if (singleValue) dndlist.setLabel(label) else dndlist.addLabel(label)
        if ( changed ) {
            fragment.setChanged(editor.labels, editor.idUndoEdit)
        }
        editor.autoComplete.setText("")
    }
}