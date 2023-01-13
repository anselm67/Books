package com.anselm.books.ui.edit

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.databinding.EditFieldLayoutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

open class TextEditor(
    fragment: Fragment,
    inflater: LayoutInflater,
    book: Book,
    val labelId: Int,
    val getter: KProperty1.Getter<Book, String>,
    val setter: KMutableProperty1.Setter<Book, String>,
    val checker: ((String) -> Boolean)? = null
): Editor(fragment, inflater, book) {
    private var _binding: EditFieldLayoutBinding? = null
    protected val editor get() = _binding!!

    override fun setup(container: ViewGroup?): View {
        super.setup(container)
        _binding = EditFieldLayoutBinding.inflate(inflater, container, false)
        editor.idEditLabel.text = fragment.getText(labelId)
        editor.idEditText.let {
            it.setText(getter(book))
            it.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

                override fun afterTextChanged(s: Editable?) {
                    val value = s.toString().trim()
                    if (checker != null && ! checker.invoke(value)) {
                        setInvalid(it, editor.idUndoEdit)
                    } else if (value != getter(book) ) {
                        setChanged(it, editor.idUndoEdit)
                    } else {
                        setUnchanged(it, editor.idUndoEdit)
                    }
                }
            })
            // Sets up a layout listener to enable scrolling on this EditText.
            setupScrollEnableListener(it)
        }
        editor.idUndoEdit.setOnClickListener {
            editor.idEditText.setText(getter(book))
        }
        // Marks the field invalid immediately. This is for books that are being
        // manually inserted which have empty mandatory fields such as title.
        fragment.lifecycleScope.launch(Dispatchers.Main) {
            if (checker != null && !checker.invoke(getter(book))) {
                setInvalid(editor.idEditText, editor.idUndoEdit)
            }
        }
        return editor.root
    }

    private fun setupScrollEnableListener(editText: EditText) {
        editText.addOnLayoutChangeListener(object: View.OnLayoutChangeListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onLayoutChange(
                v: View?, left: Int, top: Int, right: Int,
                bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
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
        return value != getter(book)
    }

    override fun saveChange() {
        setter(book, editor.idEditText.text.toString().trim())
    }

    override fun isValid(): Boolean {
        return if (checker != null) {
            val value = editor.idEditText.text.toString().trim()
            checker.invoke(value)
        } else {
            true
        }
    }

    override fun extractValue(from: Book) {
        val thisValue = editor.idEditText.text.trim()
        val fromValue = getter(from)
        if (fromValue.isNotEmpty() && thisValue != fromValue) {
            app.postOnUiThread {
                editor.idEditText.setText(fromValue)
                setChanged(editor.idEditText, editor.idUndoEdit)
            }
        }
    }
}
