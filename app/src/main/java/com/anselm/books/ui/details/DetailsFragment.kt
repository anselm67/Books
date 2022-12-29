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
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.database.Book
import com.anselm.books.database.BookFields
import com.anselm.books.database.Label
import com.anselm.books.databinding.DetailsDetailsLayoutBinding
import com.anselm.books.databinding.DetailsFieldLayoutBinding
import com.anselm.books.databinding.DetailsMultiLabelLayoutBinding
import com.anselm.books.databinding.FragmentDetailsBinding
import com.anselm.books.ui.widgets.DnDList
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class DetailsFragment : Fragment() {
    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!
    private var bookId: Long = -1L
    private lateinit var navController: NavController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentDetailsBinding.inflate(inflater, container, false)
        navController = findNavController()

        val root: View = binding.root

        val repository = BooksApplication.app.repository
        val safeArgs: DetailsFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            val book: Book = repository.load(safeArgs.bookId, decorate = true)
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
                menu.findItem(R.id.idSortByDateAdded)?.isVisible = false
                menu.findItem(R.id.idSortByTitle)?.isVisible = false
                menu.findItem(R.id.idSearchView)?.isVisible = false
                menu.findItem(R.id.idEditBook)?.isVisible = true
                menu.findItem(R.id.idSaveBook)?.isVisible = false
                menu.findItem(R.id.idGotoSearchView)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.idEditBook && bookId >= 0) {
                    val action =
                        DetailsFragmentDirections.actionDetailsFragmentToEditFragment(bookId)
                    navController.navigate(action)
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
            binding.idSearchEnabled.visibility = View.VISIBLE
            binding.valueView.setOnClickListener {
                onClick((it as TextView).text.toString())
            }
        } else {
            binding.idSearchEnabled.visibility = View.GONE
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
        arrayOf<Triple<String, View, TextView>>(
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
        ).forEach { (columnName, containerView, view) ->
            val value = book.get(columnName)
            if (value == "") {
                containerView.visibility = View.GONE
                view.visibility = View.GONE
            } else {
                view.text = value
            }

        }
        detailsView?.addView(container)
    }

    private fun bindMultiLabelField(
        inflater: LayoutInflater,
        detailsView: ViewGroup?,
        label: String,
        labels: List<Label>
    ) {
        // No labels? Skip.
        if ( labels.isEmpty() ) {
            return
        }
        // Inflates this field's container.
        val container = LinearLayout(detailsView?.context)
        container.layoutDirection = View.LAYOUT_DIRECTION_RTL
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        val binding = DetailsMultiLabelLayoutBinding.inflate(inflater, container, true)
        binding.labels.layoutManager = LinearLayoutManager(binding.labels.context)
        binding.labelView.text = label
        DnDList(binding.labels, labels) {
            // FIXME - SearchFragment should take a list of Pair<Int, Long>
            val type = labels[0].type
            val genreId = if (type == Label.Genres)  it.id else 0L
            val authorId = if (type == Label.Authors)  it.id else 0L

            val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                query="", location=0L, genre = genreId, publisher = 0L, author=authorId)
            navController.navigate(action)
        }
        detailsView?.addView(container)
    }

    private fun FragmentDetailsBinding.bind(inflater: LayoutInflater, book: Book) {
        val app = BooksApplication.app
        val uri = app.getCoverUri(book)
        app.title = book.title
        // Main part of the details.
        titleView.text = book.title
        subtitleView.text = book.subtitle
        if (uri != null) {
            Glide.with(app.applicationContext)
                .load(uri).centerCrop()
                .placeholder(R.mipmap.ic_book_cover)
                .into(coverImageView)
        } else {
            Glide.with(app.applicationContext)
                .load(R.mipmap.ic_book_cover)
                .into(coverImageView)
        }
        // Authors and Genres.
        bindMultiLabelField(inflater, detailsView, getString(R.string.authorLabel),
            book.getLabels(Label.Authors)
        )
        bindMultiLabelField(inflater, detailsView, getString(R.string.genreLabel),
            book.getLabels(Label.Genres)
        )
        // Details.
        bindDetails(inflater, detailsView, book)
        // Remaining fields.
        val fields = mutableListOf<Triple<Int, String, ((String?) -> Unit)?>>(
            Triple(R.string.publisherLabel, BookFields.PUBLISHER) {
                val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                    query="", location=0L, genre=0L,
                    publisher = book.firstLabel(Label.Publisher)?.id ?: 0L,
                    author=0L)
                navController.navigate(action)
            },
            Triple(R.string.yearPublishedLabel, BookFields.YEAR_PUBLISHED, null),
            Triple(R.string.physicalLocationLabel, BookFields.PHYSICAL_LOCATION) {
                val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                    query="",
                    location = book.firstLabel(Label.PhysicalLocation)?.id ?: 0L,
                    genre=0L, publisher = 0L, author=0L)
                navController.navigate(action)
            },
            Triple(R.string.summaryLabel, BookFields.SUMMARY, null),
            Triple(R.string.dateAddedLabel, BookFields.DATE_ADDED, null),
        )
        if (app.prefs.getBoolean("display_last_modified", false)) {
            fields.add(Triple(R.string.lastModifiedLabel, BookFields.LAST_MODIFIED, null))
        }
        if (app.prefs.getBoolean("display_book_id", false)) {
            fields.add(Triple(R.string.bookIdLabel, BookFields.BOOK_ID, null))
        }

        fields.forEach { (labelId, columnName, onClick) ->
            bindField(
                inflater,
                detailsView,
                app.applicationContext.getString(labelId),
                book.get(columnName),
                onClick
            )
        }
    }
}