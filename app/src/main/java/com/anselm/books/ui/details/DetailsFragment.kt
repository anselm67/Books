package com.anselm.books.ui.details

import android.app.ActionBar.LayoutParams
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.anselm.books.Book
import com.anselm.books.BookFields
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.databinding.DetailsDetailsLayoutBinding
import com.anselm.books.databinding.DetailsFieldLayoutBinding
import com.anselm.books.databinding.FragmentDetailsBinding
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class DetailsFragment : Fragment() {
    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val repository = (activity?.application as BooksApplication).repository
        val picasso = (activity?.application as BooksApplication).picasso
        val safeArgs: DetailsFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            val book: Book = repository.getBook(safeArgs.bookId)
            binding.bind(inflater, picasso, book)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}

private fun bindField(inflater: LayoutInflater, detailsView: ViewGroup?, label: String, value: String) {
    if ( value == "" ) {
        return
    }
    val container = LinearLayout(detailsView?.context)
    container.layoutDirection = View.LAYOUT_DIRECTION_RTL
    container.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
    )
    val binding = DetailsFieldLayoutBinding.inflate(inflater, container, true)
    binding.labelView.text = label
    binding.valueView.text = value
    detailsView?.addView(container)
}

private fun bindDetails(inflater: LayoutInflater, detailsView: ViewGroup?, book: Book) {
    if (book.numberOfPages == "" && book.language == "" && book.isbn == "")
        return
    val container = LinearLayout(detailsView?.context)
    container.layoutDirection = View.LAYOUT_DIRECTION_RTL
    container.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
    )
    val binding = DetailsDetailsLayoutBinding.inflate(inflater, container, true)
    val fields = arrayOf<Triple<String, View, TextView>>(
        Triple(BookFields.ISBN,
            binding.isbnContainerView,
            binding.isbnView),
        Triple(BookFields.LANGUAGE,
            binding.languageContainerView,
            binding.languageView),
        Triple(BookFields.NUMBER_OF_PAGES,
            binding.numberOfPagesContainerView,
            binding.numberOfPageView),
    )
    for (t in fields) {
        val value = book.get(t.first)
        if (value == "") {
            t.second.visibility = View.GONE
            t.third.visibility = View.GONE
        } else {
            t.third.text = value
        }
    }
    detailsView?.addView(container)
}

private val FIELDS = arrayOf<Pair<Int,String>>(
    Pair(R.string.publisherLabel, BookFields.PUBLISHER),
    Pair(R.string.genreLabel, BookFields.GENRE),
    Pair(R.string.yearPublishedLabel, BookFields.YEAR_PUBLISHED),
    Pair(R.string.physicalLocationLabel, BookFields.PHYSICAL_LOCATION),
    Pair(R.string.summaryLabel, BookFields.SUMMARY),
    Pair(R.string.dateAddedLabel, BookFields.DATE_ADDED)
)

private fun FragmentDetailsBinding.bind(inflater: LayoutInflater, picasso: Picasso, book: Book) {
    val context = detailsView.context
    // Main part of the details.
    titleView.text = book.title
    subtitleView.text = book.subtitle
    if (book.imgUrl != "") {
        picasso.load(book.imgUrl).fit().centerCrop()
            .placeholder(R.mipmap.ic_book_cover)
            .into(coverImageView)
    } else {
        picasso.load(R.mipmap.ic_book_cover)
            .into(coverImageView)
    }
    // Author.
    bindField(inflater, detailsView,
        context.getString(R.string.authorLabel),
        book.get(BookFields.AUTHOR))
    // Details.
    bindDetails(inflater, detailsView, book)
    // Remaining fields.
    for (pair in FIELDS) {
        bindField(inflater, detailsView, context.getString(pair.first), book.get(pair.second))
    }
}