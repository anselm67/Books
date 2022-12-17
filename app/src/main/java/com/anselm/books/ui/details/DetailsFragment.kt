package com.anselm.books.ui.details

import android.app.ActionBar.LayoutParams
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.Book
import com.anselm.books.BookFields
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.databinding.DetailsDetailsLayoutBinding
import com.anselm.books.databinding.DetailsFieldLayoutBinding
import com.anselm.books.databinding.FragmentDetailsBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class DetailsFragment : Fragment() {
    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!
    private var bookId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentDetailsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val repository = (activity?.application as BooksApplication).repository
        val safeArgs: DetailsFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            val book: Book = repository.getBook(safeArgs.bookId)
            bookId = book.id
            binding.bind(inflater, book)
        }

        handleMenu(requireActivity())
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.findItem(R.id.idSearchView)?.isVisible = false
                menu.findItem(R.id.idEditBook)?.isVisible = true
                menu.findItem(R.id.idSaveBook)?.isVisible = false
                menu.findItem(R.id.idGotoSearchView)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.idEditBook && bookId >= 0) {
                    val action =
                        DetailsFragmentDirections.actionDetailsFragmentToEditFragment(bookId)
                    findNavController().navigate(action)
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun bindField(
        inflater: LayoutInflater,
        detailsView: ViewGroup?,
        label: String,
        value: String,
        onClick: ((String?) -> Unit)? = null
    ) {
        if (value == "") {
            return
        }
        // Inflates this field's container.
        val container = LinearLayout(detailsView?.context)
        container.layoutDirection = View.LAYOUT_DIRECTION_RTL
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        val binding = DetailsFieldLayoutBinding.inflate(inflater, container, true)
        // Accounts for navigation to search if enabled.
        if (onClick != null) {
            binding.valueView.setOnClickListener {
                onClick((it as TextView).text.toString())
            }
        }
        // Fills in the blanks.
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
            Triple(
                BookFields.ISBN,
                binding.isbnContainerView,
                binding.isbnView
            ),
            Triple(
                BookFields.LANGUAGE,
                binding.languageContainerView,
                binding.languageView
            ),
            Triple(
                BookFields.NUMBER_OF_PAGES,
                binding.numberOfPagesContainerView,
                binding.numberOfPageView
            ),
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

    private val fields = arrayOf<Triple<Int, String, ((String?) -> Unit)?>>(
        Triple(R.string.publisherLabel, BookFields.PUBLISHER) {
            val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                publisher = it.toString()
            )
            findNavController().navigate(action)
        },
        Triple(R.string.genreLabel, BookFields.GENRE) {
            val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                genre = it.toString()
            )
            findNavController().navigate(action)
        },
        Triple(R.string.yearPublishedLabel, BookFields.YEAR_PUBLISHED, null),
        Triple(R.string.physicalLocationLabel, BookFields.PHYSICAL_LOCATION) {
            val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                location = it.toString()
            )
            findNavController().navigate(action)
        },
        Triple(R.string.summaryLabel, BookFields.SUMMARY, null),
        Triple(R.string.dateAddedLabel, BookFields.DATE_ADDED, null)
    )

    private fun FragmentDetailsBinding.bind(inflater: LayoutInflater, book: Book) {
        val app = BooksApplication.app
        app.title = book.title
        // Main part of the details.
        titleView.text = book.title
        subtitleView.text = book.subtitle
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
        // Author.
        bindField(
            inflater, detailsView,
            app.applicationContext.getString(R.string.authorLabel),
            book.get(BookFields.AUTHOR)
        ) {
            val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                author = it.toString()
            )
            root.findNavController().navigate(action)
        }
        // Details.
        bindDetails(inflater, detailsView, book)
        // Remaining fields.
        for (triple in fields) {
            bindField(
                inflater,
                detailsView,
                app.applicationContext.getString(triple.first),
                book.get(triple.second),
                triple.third
            )
        }
    }
}