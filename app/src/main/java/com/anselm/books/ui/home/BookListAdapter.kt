package com.anselm.books.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.anselm.books.Book
import com.anselm.books.databinding.RecyclerviewBookItemBinding

class BookListAdapter : PagingDataAdapter<Book, BookViewHolder>(BooksComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder =
        BookViewHolder(RecyclerviewBookItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        ))

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val current = getItem(position)
        if (current != null) {
            holder.bind(current)
        }
    }



    class BooksComparator: DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }
    }
}