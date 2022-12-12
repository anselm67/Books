package com.anselm.books.ui.edit

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.anselm.books.Book
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentEditBinding
import com.anselm.books.ui.details.DetailsFragmentArgs
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class EditFragment: Fragment() {
    private var bookId: Int = -1
    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val repository = (activity?.application as BooksApplication).repository
        val safeArgs: DetailsFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            val book: Book = repository.getBook(safeArgs.bookId)
            bookId = book.id
            binding.bind(book)
        }

        handleMenu(requireActivity())
        return root
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.findItem(R.id.idSearchView)?.isVisible = false
                menu.findItem(R.id.idEditBook)?.isVisible = false
                menu.findItem(R.id.idSaveBook)?.isVisible = true
                menu.findItem(R.id.idGotoSearchView)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.idSaveBook) {
                    Log.d(TAG, "Saving book $bookId.")
                }
                return false
            }
        })
    }
}

private fun FragmentEditBinding.bind(book: Book) {
    val app = BooksApplication.app
    // Main part of the details.
    titleView.text = book.title
    subtitleView.text = book.subtitle
    authorView.text = book.author
    if (book.imageFilename != "") {
        Glide.with(app.applicationContext)
            .load(app.getCoverUri(book.imageFilename)).centerCrop()
            .placeholder(R.mipmap.ic_book_cover)
            .into(coverImageView)
    } else {
        Glide.with(app.applicationContext)
            .load(R.mipmap.ic_book_cover)
            .into(coverImageView)
    }
}