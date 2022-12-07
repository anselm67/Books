package com.anselm.books.ui.home

import android.view.View
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.Book
import com.anselm.books.R
import com.anselm.books.databinding.RecyclerviewBookItemBinding
import com.bumptech.glide.Glide
import java.io.File

class BookViewHolder(
    private val binding: RecyclerviewBookItemBinding,
    private val onClick: (book: Book) -> Unit
): RecyclerView.ViewHolder(binding.root) {
    fun bind(book: Book) {
        binding.titleView.text = book.title
        binding.authorView.text = book.author
        if (book.imageFilename != "") {
            // TODO Don't recompute these files over and over. Also in [DetailsFragment]
            val images = File(binding.root.context.filesDir, "import")
            val imgUri = File(images, book.imageFilename).toUri()
            Glide.with(binding.root.context)
                .load(imgUri)
                .placeholder(R.mipmap.ic_book_cover)
                .centerCrop()
                .into(binding.coverImageView)
        } else {
            Glide.with(binding.root.context)
                .load(R.mipmap.ic_book_cover)
                .centerCrop()
                .into(binding.coverImageView)
        }
        binding.root.setOnClickListener { _: View ->
            book.let { this.onClick(it) }
        }
    }
}