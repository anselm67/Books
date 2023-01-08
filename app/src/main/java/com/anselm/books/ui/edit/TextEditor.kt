package com.anselm.books.ui.edit

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.anselm.books.databinding.EditFieldLayoutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


open class TextEditor(
    fragment: EditFragment,
    inflater: LayoutInflater,
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
