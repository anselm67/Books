package com.anselm.books.ui.home

import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.Book
import com.anselm.books.R
import com.anselm.books.databinding.RecyclerviewBookItemBinding
import com.squareup.picasso.Picasso

class BookViewHolder(
    private val binding: RecyclerviewBookItemBinding,
    private val picasso: Picasso
): RecyclerView.ViewHolder(binding.root) {
    fun bind(book: Book) {
        binding.titleView.text = book.title
        binding.authorView.text = book.author
        if (book.imgUrl != "") {
            picasso
                .load(book.imgUrl).fit().centerCrop()
                .placeholder(R.mipmap.ic_book_cover)
                .into(binding.coverImageView)
        } else {
            picasso.load(R.mipmap.ic_book_cover)
                .into(binding.coverImageView)
        }
    }
}