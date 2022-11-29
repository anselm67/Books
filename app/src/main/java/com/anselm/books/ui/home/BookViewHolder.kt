package com.anselm.books.ui.home

import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.Book
import com.anselm.books.databinding.RecyclerviewBookItemBinding

class BookViewHolder(
    private val binding: RecyclerviewBookItemBinding
): RecyclerView.ViewHolder(binding.root) {

    fun bind(book: Book) {
        binding.titleView.text = book.title
        binding.authorView.text = book.author
    }
}