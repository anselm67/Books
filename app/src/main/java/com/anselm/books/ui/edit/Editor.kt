package com.anselm.books.ui.edit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

abstract class Editor(
    open val fragment: Fragment,
    open val inflater: LayoutInflater,
    open val editorStatusListener: EditorStatusListener? = null,
) {

    abstract fun setup(container: ViewGroup?): View?
    abstract fun isChanged(): Boolean
    abstract fun saveChange()

    open fun isValid(): Boolean = true
}

