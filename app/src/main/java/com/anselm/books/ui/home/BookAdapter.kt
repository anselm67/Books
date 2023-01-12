package com.anselm.books.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.anselm.books.database.Book
import com.anselm.books.databinding.RecyclerviewBookItemBinding

class BookAdapter (
    private val onClick: (Book) -> Unit,
    private val selectionListener: SelectionListener,
) : PagingDataAdapter<Book, BookViewHolder>(BooksComparator())
{
    private val selected: MutableSet<Book> = emptySet<Book>().toMutableSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder =
        BookViewHolder(
            RecyclerviewBookItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ), { // onClick
                if ( selected.size > 0 ) {
                    select(it)
                } else {
                    getItem(it)?.let { onClick(it) }
                }
            }
        ) { // Long click handler for selection.
            select(it)
        }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = getItem(position)
        if (book != null) {
            holder.bind(book, selected.contains(book))
        } else {
            holder.hide()
        }
    }

    class BooksComparator: DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem == newItem
        }
    }

    private fun select(position: Int) {
        val book = getItem(position) ?: return
        if (selected.contains(book)) {
            selected.remove(book)
        } else {
            selected.add(book)
        }
        if (selected.size == 1) {
            selectionListener.onSelectionStart()
        } else if (selected.size == 0) {
            selectionListener.onSelectionStop()
        }
        selectionListener.onSelectionChanged(selected.size)
        notifyItemChanged(position)
    }

    fun getSelectedBookIds(): List<Long> {
        return selected.map { it.id }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun cancelSelection() {
        if (selected.size > 0) {
            selected.clear()
            selectionListener.onSelectionStop()
            selectionListener.onSelectionChanged(0)
            notifyDataSetChanged()
        }
    }
}