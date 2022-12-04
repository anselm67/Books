package com.anselm.books.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.anselm.books.Book
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.databinding.FragmentDetailsBinding
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
            binding.titleView.text = book.title
            binding.authorView.text = book.author
            bindText(binding.isbnView, binding.isbnLabelView, book.isbn)
            bindText(binding.yearPublishedView, binding.yearPublishedLabelView, book.yearPublished)
            bindText(binding.numberOfPagesView, binding.numberOfPagesLabelView, book.numberOfPages)
            bindText(binding.physicalLocationView, binding.physicalLocationLabelView, book.physicalLocation)
            binding.summaryView.text = book.summary
            if (book.imgUrl != "") {
                picasso.load(book.imgUrl).fit().centerCrop()
                    .placeholder(R.mipmap.ic_book_cover)
                    .into(binding.coverImageView)
            } else {
                picasso.load(R.mipmap.ic_book_cover)
                    .into(binding.coverImageView)
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindText(textView: TextView, labelView: View, value: String) {
        if (value != "") {
            textView.text = value
            textView.visibility = View.VISIBLE
            labelView.visibility = View.VISIBLE
        } else {
            textView.visibility = View.GONE
            labelView.visibility = View.GONE
        }
   }
}