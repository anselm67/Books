package com.anselm.books.ui.scan

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R


class ScanItemTouchHelper(
    private val adapter: IsbnArrayAdapter,
): SimpleCallback(0, RIGHT) {
    private var icon: Drawable = ContextCompat.getDrawable(
        app.applicationContext,
        R.drawable.ic_baseline_delete_24
    )!!

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        adapter.removeAt(viewHolder.bindingAdapterPosition)
    }

    // https://medium.com/@zackcosborn/step-by-step-recyclerview-swipe-to-delete-and-undo-7bbae1fce27e
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
                val padding = (20 /* dp */ * app.displayMetrics.density).toInt()
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