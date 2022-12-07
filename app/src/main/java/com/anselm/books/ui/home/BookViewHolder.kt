package com.anselm.books.ui.home

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.Book
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.databinding.RecyclerviewBookItemBinding
import com.bumptech.glide.Glide

class BookViewHolder(
    private val binding: RecyclerviewBookItemBinding,
    private val onClick: (book: Book) -> Unit
): RecyclerView.ViewHolder(binding.root) {
    fun bind(book: Book) {
        val app = BooksApplication.app
        binding.titleView.text = book.title
        binding.authorView.text = book.author
        if (book.imageFilename != "") {
            Glide.with(app.applicationContext)
                .load(app.getCoverUri(book.imageFilename))
                .placeholder(R.mipmap.ic_book_cover)
                .centerCrop()
                .into(binding.coverImageView)
        } else {
            Glide.with(app.applicationContext)
                .load(R.mipmap.ic_book_cover)
                .centerCrop()
                .into(binding.coverImageView)
        }
        binding.root.setOnClickListener { _: View ->
            book.let { this.onClick(it) }
        }
    }
}