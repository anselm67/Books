package com.anselm.books.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.anselm.books.database.Book
import com.anselm.books.databinding.RecyclerviewBookItemBinding

class BookAdapter (private val onClick: (Book) -> Unit)
    : PagingDataAdapter<Book, BookViewHolder>(BooksComparator())
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder =
        BookViewHolder(RecyclerviewBookItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        ), onClick)

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val current = getItem(position)
        if (current != null) {
            holder.bind(current)
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
}