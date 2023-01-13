package com.anselm.books.ui.edit

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R

abstract class Editor(
    open val fragment: Fragment,
    open val inflater: LayoutInflater,
) {
    private val context by lazy { fragment.requireContext() }
    private var validBorder: Drawable? = null
    private var invalidBorder: Drawable? = null
    private var changedBorder: Drawable? = null

    open fun setup(container: ViewGroup?): View? {
        validBorder = ResourcesCompat.getDrawable(
            app.resources, R.drawable.textview_border, null)!!
        invalidBorder = ResourcesCompat.getDrawable(
            app.resources, R.drawable.textview_border_invalid, null)
        changedBorder = ResourcesCompat.getDrawable(
            app.resources, R.drawable.textview_border_changed, null)
        return null
    }

    fun setChanged(editor: View, undoButton: ImageButton) {
        editor.background = changedBorder
        undoButton.visibility = View.VISIBLE
        undoButton.setColorFilter(
            ContextCompat.getColor(context, R.color.editorValueChanged)
        )
    }

    fun setInvalid(editor: View, undoButton: ImageButton) {
        editor.background = invalidBorder
        undoButton.visibility = View.VISIBLE
        undoButton.setColorFilter(
            ContextCompat.getColor(context, R.color.editorValueInvalid)
        )
    }

    fun setUnchanged(editor: View, undoButton: ImageButton) {
        editor.background = validBorder
        undoButton.visibility = View.GONE
        undoButton.setColorFilter(
            ContextCompat.getColor(context, R.color.editorValueUnchanged)
        )
    }

    abstract fun isChanged(): Boolean
    abstract fun saveChange()

    open fun isValid(): Boolean = true
}

