package com.anselm.books.ui.edit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

abstract class Editor(
    open val fragment: EditFragment,
    open val inflater: LayoutInflater
) {

    abstract fun setup(container: ViewGroup?): View?
    abstract fun isChanged(): Boolean
    abstract fun saveChange()

    open fun isValid(): Boolean = true
}

