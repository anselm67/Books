package com.anselm.books.ui.edit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.database.BookFields
import com.anselm.books.databinding.EditYearLayoutBinding
import kotlin.reflect.KMutableProperty1

class YearEditor(
    fragment: Fragment,
    inflater: LayoutInflater,
    book: Book,
    onChange: ((Editor<String>) -> Unit)? = null,
    private val yearProperty: KMutableProperty1<Book, String>,
): Editor<String>(fragment, inflater, book, onChange) {
    private var _binding: EditYearLayoutBinding? = null
    private val editor get() = _binding!!

    private fun getEditorValue(): Int {
        return (editor.yearPublished100Picker.value * 100
                + editor.yearPublished10Picker.value * 10
                + editor.yearPublished1Picker.value)
    }

    private fun setEditorValue(value: Int?) {
        if (value == null) {
            return
        }
        if (value in BookFields.MIN_PUBLISHED_YEAR..BookFields.MAX_PUBLISHED_YEAR) {
            editor.yearPublished100Picker.value = value / 100
            editor.yearPublished10Picker.value = (value / 10) % 10
            editor.yearPublished1Picker.value = value % 10
        }
    }

    override fun setup(container: ViewGroup?): View {
        super.setup(container)
        _binding = EditYearLayoutBinding.inflate(inflater, container, false)
        editor.yearPublished100Picker.minValue = BookFields.MIN_PUBLISHED_YEAR / 100
        editor.yearPublished100Picker.maxValue = BookFields.MAX_PUBLISHED_YEAR / 100
        editor.yearPublished10Picker.minValue = 0
        editor.yearPublished10Picker.maxValue = 9
        editor.yearPublished1Picker.minValue = 0
        editor.yearPublished1Picker.maxValue = 9
        setEditorValue(yearProperty.getter(book).toIntOrNull() ?: 0)
        val onValueChanged = NumberPicker.OnValueChangeListener { _, _, _ ->
            val newValue = getEditorValue()
            if (newValue != yearProperty.getter(book).toIntOrNull()) {
                setChanged(editor.yearPublishedView, editor.idUndoEdit)
            } else {
                setUnchanged(editor.yearPublishedView, editor.idUndoEdit)
            }
        }
        editor.yearPublished100Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished10Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished1Picker.setOnValueChangedListener(onValueChanged)
        editor.idUndoEdit.setOnClickListener {
            setEditorValue(yearProperty.getter(book).toIntOrNull() ?: 0)
            setUnchanged(editor.yearPublishedView, editor.idUndoEdit)
        }

        return editor.root
    }

    override fun isChanged(): Boolean {
        val value = getEditorValue()
        return (yearProperty.getter(book).toIntOrNull() ?: 0) != value
    }

    override fun saveChange() {
        val value = getEditorValue()
        yearProperty.setter(book, value.toString())
    }

    override fun extractValue(from: Book) {
        val thisValue = getEditorValue()
        val fromValue = yearProperty.getter(from).toIntOrNull()
        if (fromValue != null && fromValue != thisValue) {
            app.postOnUiThread {
                setEditorValue(fromValue)
                if (yearProperty.getter(from) != yearProperty.getter(book)) {
                    setChanged(editor.yearPublishedView, editor.idUndoEdit)
                } else {
                    setUnchanged(editor.yearPublishedView, editor.idUndoEdit)
                }
            }
        }
    }

    override fun getValue(): String {
        return getEditorValue().toString()
    }
}
