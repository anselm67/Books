package com.anselm.books.ui.cleanup

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.END
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG

/**
 * For merging items in recycler view:
 * https://stackoverflow.com/questions/70226403/merge-items-in-recycler-view-when-dragged-dropped-on-one-another-in-android
 */

class CleanUpLabelItemTouchHelper(
    private val fragment: CleanUpLabelFragment,
) :  SimpleCallback(UP or DOWN or START or END, RIGHT) {
    private var icon: Drawable = ContextCompat.getDrawable(
        BooksApplication.app.applicationContext,
        R.drawable.ic_baseline_delete_24
    )!!

    var target: RecyclerView.ViewHolder? = null
    var moving: RecyclerView.ViewHolder? = null

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        fragment.promptForDelete(viewHolder.bindingAdapterPosition)
    }

    override fun onMove(recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder): Boolean {
        this.target?.let { (it as LabelCleanupArrayAdapter.ViewHolder).offTarget() }
        this.target = target
        this.moving = viewHolder
        (target as LabelCleanupArrayAdapter.ViewHolder).onTarget()
        Log.d(TAG, "onMove ${this.target == this.moving}")
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.5f
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        Log.d(TAG, "clearView")
        target?.let { (it as LabelCleanupArrayAdapter.ViewHolder).offTarget() }
        fragment.promptForMerge(
            moving?.bindingAdapterPosition ?: -1,
            target?.bindingAdapterPosition ?: -1,
        )
        target = null
        moving = null
        viewHolder.itemView.alpha = 1f
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // Get RecyclerView item from the ViewHolder
            val item: View = viewHolder.itemView

            val rect = if ( dX > 0) {

                val dx = dX.toInt()
                val padding = (2 /* dp */ * BooksApplication.app.displayMetrics.density).toInt()
                val width = item.bottom - item.top - 2 * padding
                Rect(
                    dx - width, item.top + padding, dx, item.bottom - padding
                )
            } else {
                // Draw Rect with varying left side, equal to the item's right side plus negative displacement dX
                // THIS HASN't BEEN TESTED AS WE SWIPE RIGHT ONLY FOR NOW.
                Rect(
                    item.right + dX.toInt(),
                    item.top,
                    item.right,
                    item.bottom,
                )
            }
            DrawableCompat.setTint(icon, Color.RED)
            icon.bounds = rect
            icon.draw(c)
        }
    }
}

