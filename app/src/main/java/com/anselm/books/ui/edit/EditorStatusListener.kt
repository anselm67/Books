package com.anselm.books.ui.edit

import android.view.View
import android.widget.ImageButton

abstract class EditorStatusListener {
    abstract fun setChanged(container: View, undoButton: ImageButton)
    abstract fun setUnchanged(container: View, undoButton: ImageButton)
    abstract fun setInvalid(container: View, undoButton: ImageButton)
    abstract fun scrollTo(view: View)
    abstract fun checkCameraPermission(): Boolean
}