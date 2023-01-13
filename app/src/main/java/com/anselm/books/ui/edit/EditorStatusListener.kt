package com.anselm.books.ui.edit

import android.view.View

// FIXME Get rid of it: ue parent.parent for scroll and move perm to main activity.
abstract class EditorStatusListener {
    abstract fun scrollTo(view: View)
    abstract fun checkCameraPermission(): Boolean
}