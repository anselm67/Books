package com.anselm.books.ui.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.View.GONE
import android.view.View.OnFocusChangeListener
import android.view.View.OnLayoutChangeListener
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Filter
import android.widget.ImageButton
import android.widget.NumberPicker.OnValueChangeListener
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.*
import com.anselm.books.database.Book
import com.anselm.books.database.BookDao
import com.anselm.books.database.BookFields
import com.anselm.books.database.Label
import com.anselm.books.databinding.AutocompleteLabelLayoutBinding
import com.anselm.books.databinding.EditFieldLayoutBinding
import com.anselm.books.databinding.EditMultiLabelLayoutBinding
import com.anselm.books.databinding.EditSingleLabelLayoutBinding
import com.anselm.books.databinding.EditYearLayoutBinding
import com.anselm.books.databinding.FragmentEditBinding
import com.anselm.books.ui.widgets.BookFragment
import com.anselm.books.ui.widgets.DnDList
import com.bumptech.glide.Glide
import com.bumptech.glide.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.lang.Integer.max


class EditFragment: BookFragment() {
    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!
    private lateinit var book: Book

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

        val repository = app.repository

        // Parses the arguments, we can have either:
        // - A bookId, which means we want to edit/update an existing book,
        // - A Book instance which is to be created/ inserted.
        val safeArgs: EditFragmentArgs by navArgs()
        if (safeArgs.bookId > 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                book = repository.load(safeArgs.bookId, decorate = true)!!
                editors = bind(inflater, book)
            }
        } else if (safeArgs.book != null) {
            book = safeArgs.book!!
            editors = bind(inflater, book)
        } else {
            Log.d(TAG, "No books to edit.")
        }

        // Caches the borders corresponding to the various states of individual field editors.
        validBorder = getBorderDrawable(R.drawable.textview_border)
        invalidBorder = getBorderDrawable(R.drawable.textview_border_invalid)
        changedBorder = getBorderDrawable(R.drawable.textview_border_changed)

        handleMenu(listOf(
            Pair(R.id.idSaveBook) {
                saveChanges()
            },
            Pair(R.id.idDeleteBook) {
                val builder = AlertDialog.Builder(requireActivity())
                builder.setMessage(getString(R.string.delete_book_confirmation, book.title))
                    .setPositiveButton(R.string.yes) { _, _ -> deleteBook() }
                    .setNegativeButton(R.string.no) { _, _ -> }
                    .show()
            },
        ))

        setupCoverCameraLauncher()
        setupCoverPickerLauncher()

        return root
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

    private fun loadCoverImage(uri: Uri?) {
        if (uri != null) {
            Glide.with(app.applicationContext)
                .load(uri).centerCrop()
                .placeholder(R.mipmap.ic_book_cover)
                .into(binding.idCoverImage)
        } else {
            Glide.with(app.applicationContext)
                .load(R.mipmap.ic_book_cover)
                .into(binding.idCoverImage)
        }
    }

    private fun bind(inflater: LayoutInflater, book: Book): List<Editor> {
        val app = BooksApplication.app
        loadCoverImage(app.imageRepository.getCoverUri(book))
        binding.idCameraPickerButton.setOnClickListener {
            launchCoverCamera()
        }
        binding.idMediaPickerButton.setOnClickListener {
            launchCoverPicker()
        }
        // Creates and sets up an editor for every book property.
        val fields = arrayListOf(
            TextEditor(this, inflater, R.string.titleLabel,
                book::title.getter, book::title.setter) {
                it.isNotEmpty()
            },
            TextEditor(this, inflater, R.string.subtitleLabel,
                book::subtitle.getter, book::subtitle.setter),
            MultiLabelEditor(this, inflater,
                Label.Type.Authors, R.string.authorLabel,
                book::authors.getter, book::authors.setter),
            SingleLabelEditor(this, inflater,
                Label.Type.Publisher, R.string.publisherLabel,
                book::publisher.getter, book::publisher.setter),
            MultiLabelEditor(this, inflater,
                Label.Type.Genres, R.string.genreLabel,
                book::genres.getter, book::genres.setter),
            SingleLabelEditor(this, inflater,
                Label.Type.Location, R.string.physicalLocationLabel,
                book::location.getter, book::location.setter),
            TextEditor(this, inflater, R.string.isbnLabel,
                book::isbn.getter, book::isbn.setter) {
                isValidEAN13(it)
            },
            SingleLabelEditor(this, inflater,
                Label.Type.Language, R.string.languageLabel,
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

    private lateinit var coverCameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var coverPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    private var editedImageFile: File? = null
    private var editCoverBitmap: Bitmap? = null

    // http://sylvana.net/jpegcrop/exif_orientation.html
    private val exifAngles = mapOf<Int, Float>(
        1 to 0F,
        2 to 0F,
        3 to 180F,
        4 to 180F,
        5 to 90F,
        6 to 90F,
        7 to 270F,
        8 to 270F,
    )

    private fun setupCoverCameraLauncher() {
        coverCameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) {
            // No bitmap? the user cancelled on us.
            if ( ! it || editedImageFile == null) {
                return@registerForActivityResult
            }
            val exifRotation = ExifInterface(editedImageFile!!)
                .getAttribute(ExifInterface.TAG_ORIENTATION)
                ?.toIntOrNull()
            BitmapFactory.decodeFile(editedImageFile!!.path, BitmapFactory.Options().apply {
                inSampleSize = 8 // Going from about 4,000px width down to about 500px.
            }).also { cameraBitmap ->
                editCoverBitmap = Bitmap.createBitmap(
                    cameraBitmap,
                    0, 0, cameraBitmap.width, cameraBitmap.height,
                    Matrix().apply {
                       postRotate(exifAngles.getOrDefault(key = exifRotation, defaultValue = 0F))
                    },
                    true)
                Util.postOnUiThread {
                    binding.idCoverImage.setImageDrawable(
                        BitmapDrawable(resources, editCoverBitmap)
                    )
                }
            }
        }
    }

    private fun launchCoverCamera() {
        if (editedImageFile == null) {
            editedImageFile = File.createTempFile("cover_edit", ".png", app.cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
        }
        coverCameraLauncher.launch(FileProvider.getUriForFile(app.applicationContext,
            "${BuildConfig.APPLICATION_ID}.provider",
            editedImageFile!!)
        )
    }

    private fun setupCoverPickerLauncher() {
        coverPickerLauncher = registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }
            app.contentResolver.openFileDescriptor(uri, "r").use { it?.let {
                    FileInputStream(it.fileDescriptor).use { inputStream ->
                        editCoverBitmap = BitmapFactory.decodeStream(inputStream)
                    }
                }
            }
            Util.postOnUiThread {
                binding.idCoverImage.setImageDrawable(
                    BitmapDrawable(resources, editCoverBitmap)
                )
            }
            Log.d(TAG, "Got $uri")
        }
    }

    private fun launchCoverPicker() {
        coverPickerLauncher.launch(
            PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private suspend fun saveCoverImage() {
        check(editCoverBitmap != null)
        book.imageFilename = app.imageRepository.convertAndSave(book, editCoverBitmap!!)
    }

    private fun saveEditorChanges(): Boolean {
        var changed = (editCoverBitmap != null)
        editors?.forEach {
            if (it.isChanged()) {
                changed = true
                it.saveChange()
            }
        }
        return changed
    }

    private fun validateChanges():Boolean {
        return editors?.firstOrNull { ! it.isValid() } == null
    }

    private fun saveChanges() {
        // Validates the edits first, reject invalid books.
        val app = BooksApplication.app
        if ( ! validateChanges() ) {
            app.toast("Adjust highlighted fields.")
            return
        }
        // Inserts or saves only when valid.
        if (saveEditorChanges() || book.id <= 0) {
            app.loading(true)
            activity?.lifecycleScope?.launch {
                // saveCoverImage makes additional changes to the book, it needs to come first.
                saveCoverImage()
                app.repository.save(book)
            }?.invokeOnCompletion {
                app.toast("${book.title} saved.")
                app.loading(false)
                findNavController().popBackStack()
            }
        } else {
            // Nothing to save, head back.
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

private abstract class Editor(
    open val fragment: EditFragment,
    open val inflater: LayoutInflater) {

    abstract fun setup(container: ViewGroup?): View
    abstract fun isChanged(): Boolean
    abstract fun saveChange()

    open fun isValid(): Boolean = true
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
        // Marks the field invalid immediately. This is for books that are being
        // manually inserted which have empty mandatory fields such as title.
        fragment.lifecycleScope.launch(Dispatchers.Main) {
            if (checker != null && !checker!!.invoke(getter())) {
                fragment.setInvalid(editor.idEditText, editor.idUndoEdit)
            }
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

    override fun isValid(): Boolean {
        return if (checker != null) {
            val value = editor.idEditText.text.toString().trim()
            checker!!.invoke(value)
        } else {
            true
        }
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
                    sortBy = BookDao.SortByTitle,
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
                @Suppress("UNCHECKED_CAST")
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

private class LabelAutoComplete(
    val fragment: EditFragment,
    val autoComplete: AutoCompleteTextView,
    val type: Label.Type,
    val initialValue: Label? = null,
    val handleLabel: (Label) -> Unit,
    val onChange: ((String) -> Unit)? = null
) {
    private val repository = BooksApplication.app.repository
    private val initialText = initialValue?.name ?: ""

    init {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val labels = repository.getHisto(type, sortBy = BookDao.SortByTitle).map {
                repository.label(it.labelId)
            }
            val adapter = LabelArrayAdapter(
                fragment.requireActivity(),
                type,
                labels,
            )
            autoComplete.threshold = 1
            if (initialValue != null) {
                autoComplete.setText(initialValue.name, false)
            }
            autoComplete.setAdapter(adapter)
            autoComplete.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val input = s.toString()
                    if (input != initialText) {
                        onChange?.invoke(input)
                    }
                }
            })
            autoComplete.setOnItemClickListener { parent, _, position, _ ->
                handleLabel(parent.getItemAtPosition(position) as Label)
            }
            autoComplete.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
                    if ( hasFocus ) {
                        fragment.scrollTo(autoComplete.parent as View)
                    }
                }
        }
        autoComplete.setOnEditorActionListener(object: OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId != EditorInfo.IME_ACTION_DONE)
                    return false
                val text = if (v == null || v.text == null) "" else v.text.toString().trim()
                if (text != initialText) {
                    handleLabel(repository.labelB(type, text))
                }
                return false
            }

        })

    }
}

private class MultiLabelEditor(
    override val fragment: EditFragment,
    override val inflater: LayoutInflater,
    val type: Label.Type,
    val labelId: Int,
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

        // Sets up te drag and drop list view for displaying the existing labels.
        dndlist = DnDList(
            editor.labels,
            getter().toMutableList(),
            onChange = { newLabels ->
                if (newLabels != getter()) {
                    fragment.setChanged(editor.root, editor.idUndoEdit)
                } else {
                    fragment.setUnchanged(editor.root, editor.idUndoEdit)
                }
            }
        )

        // Sets up the auto-complete for entering new labels.
        LabelAutoComplete(fragment, editor.autoComplete, type,
            handleLabel = { addLabel(it) }
        )
        // Sets up the undo button.
        editor.idUndoEdit.setOnClickListener {
            fragment.setUnchanged(editor.root, editor.idUndoEdit)
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
        if ( dndlist.addLabel(label) ) {
            fragment.setChanged(editor.root, editor.idUndoEdit)
        }
        editor.autoComplete.setText("")
    }
}

private class SingleLabelEditor(
    override val fragment: EditFragment,
    override val inflater: LayoutInflater,
    val type: Label.Type,
    val labelId: Int,
    val getter: () -> Label?,
    val setter: (Label) -> Unit
): Editor (fragment, inflater) {
    private var _binding: EditSingleLabelLayoutBinding? = null
    private val editor get() = _binding!!
    private var editLabel: Label? = null
    private val origText = if (getter() == null) "" else getter()!!.name

    override fun setup(container: ViewGroup?): View {
        _binding = EditSingleLabelLayoutBinding.inflate(inflater, container, false)
        editor.idEditLabel.text = fragment.getText(labelId)

        // Sets up the auto-complete for entering new labels.
        LabelAutoComplete(fragment, editor.autoComplete, type, getter(),
            handleLabel = { setLabel(it) },
            onChange = {
                fragment.setChanged(editor.root, editor.idUndoEdit)
            }
        )
        // Sets up the undo button.
        editor.idUndoEdit.setOnClickListener {
            fragment.setUnchanged(editor.root, editor.idUndoEdit)
            editor.autoComplete.setText(origText)
        }
        return editor.root
    }

    override fun isChanged(): Boolean {
        return getter() != editLabel
    }

    override fun saveChange() {
        editLabel?.let { setter(it) }
    }

    fun setLabel(label: Label) {
        if (label != getter()) {
            editLabel = label
            fragment.setChanged(editor.root, editor.idUndoEdit)
        }
    }
}