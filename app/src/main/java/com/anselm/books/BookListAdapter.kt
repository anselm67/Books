package com.anselm.books

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class BookListAdapter : ListAdapter<Book, BookListAdapter.BookViewHolder>(BooksComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        return BookViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    class BookViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val idView: TextView = itemView.findViewById(R.id.idView)
        private val titleView: TextView = itemView.findViewById(R.id.titleView)
        private val authorView: TextView = itemView.findViewById(R.id.authorView)

        fun bind(book: Book) {
            idView.text = book.id.toString()
            titleView.text = book.title
            authorView.text = book.author
        }

        companion object {
            fun create(parent: ViewGroup): BookViewHolder {
                val view: View = LayoutInflater.from(parent.context)
                    .inflate(R.layout.recyclerview_book_item, parent, false)
                return BookViewHolder(view)
            }
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