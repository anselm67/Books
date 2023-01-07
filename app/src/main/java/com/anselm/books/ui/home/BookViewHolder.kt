package com.anselm.books.ui.home

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.database.Book
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.databinding.RecyclerviewBookItemBinding
import com.bumptech.glide.Glide

class BookViewHolder(
    private val binding: RecyclerviewBookItemBinding,
    private val onClick: (book: Book) -> Unit
): RecyclerView.ViewHolder(binding.root) {
    fun bind(book: Book) {
        show()
        val app = BooksApplication.app
        val uri = app.imageRepository.getCoverUri(book)
        binding.titleView.text = book.title
        binding.authorView.text = book.authors.joinToString { it.name }
        if (uri != null) {
            Glide.with(app.applicationContext)
                .load(uri)
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

    fun hide() {
        binding.titleView.isVisible = false
        binding.authorView.isVisible = false
        binding.coverImageView.isVisible = false
        binding.idRuler.isVisible = false
    }

    fun show() {
        binding.titleView.isVisible = true
        binding.authorView.isVisible = true
        binding.coverImageView.isVisible = true
        binding.idRuler.isVisible = true
    }
}