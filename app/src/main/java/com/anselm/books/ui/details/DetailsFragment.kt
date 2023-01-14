package com.anselm.books.ui.details

import android.app.ActionBar.LayoutParams
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication
import com.anselm.books.GlideApp
import com.anselm.books.R
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import com.anselm.books.databinding.DetailsDetailsLayoutBinding
import com.anselm.books.databinding.DetailsFieldLayoutBinding
import com.anselm.books.databinding.DetailsMultiLabelLayoutBinding
import com.anselm.books.databinding.FragmentDetailsBinding
import com.anselm.books.databinding.RecyclerviewDetailsLabelItemBinding
import com.anselm.books.ui.widgets.BookFragment
import com.anselm.books.ui.widgets.MenuItemHandler
import kotlinx.coroutines.launch

class DetailsFragment : BookFragment() {
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

        val repository = BooksApplication.app.repository
        val safeArgs: DetailsFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            val book: Book? = repository.load(safeArgs.bookId, decorate = true)
            if (book != null) {
                bookId = book.id
                binding.bind(inflater, book)

                // We have to wait for the book to setup the menu.
                handleMenu(
                    MenuItemHandler(R.id.idDeleteBook, {
                        val builder = AlertDialog.Builder(requireActivity())
                        builder.setMessage(getString(R.string.delete_book_confirmation, book.title))
                            .setPositiveButton(R.string.yes) { _, _ -> deleteBook(book) }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .show()
                    }),
                )

            } else {
                navController.popBackStack()
            }
        }

        binding.fabEditButton.setOnClickListener {
            val action = DetailsFragmentDirections.toEditFragment(bookId)
            navController.navigate(action)
        }

        return binding.root
    }

    private fun deleteBook(book: Book) {
        val app = BooksApplication.app
        if (book.id >= 0) {
            app.applicationScope.launch {
                app.repository.deleteBook(book)
                app.toast(getString(R.string.book_deleted, book.title))
                app.postOnUiThread {
                    findNavController().popBackStack()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
        if (book.numberOfPages == "" && book.language == null && book.isbn == "")
            return
        val container = LinearLayout(detailsView?.context)
        container.layoutDirection = View.LAYOUT_DIRECTION_RTL
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        val binding = DetailsDetailsLayoutBinding.inflate(inflater, container, true)
        arrayOf<Triple<() -> String, View, TextView>>(
            Triple(
                book::isbn.getter,
                binding.isbnContainerView,
                binding.isbnView
            ),
            Triple(
                { book::language.getter()?.name ?: "" },
                binding.languageContainerView,
                binding.languageView
            ),
            Triple(
                book::numberOfPages.getter,
                binding.numberOfPagesContainerView,
                binding.numberOfPageView
            ),
        ).forEach { (getter, containerView, view) ->
            val value = getter()
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
        binding.labels.adapter = LabelArrayAdapter(
            labels,
            onClick = {
                val action = DetailsFragmentDirections.toSearchFragment(
                    Query(filters = mutableListOf(Query.Filter(it))))
                navController.navigate(action)
            }
        )
        binding.labelView.text = label
        detailsView?.addView(container)
    }

    private fun FragmentDetailsBinding.bind(inflater: LayoutInflater, book: Book) {
        val app = BooksApplication.app
        val uri = app.imageRepository.getCoverUri(book)
        app.title = book.title
        // Main part of the details.
        titleView.text = book.title
        subtitleView.text = book.subtitle
        if (uri != null) {
            GlideApp.with(app.applicationContext)
                .load(uri).centerCrop()
                .placeholder(R.drawable.broken_image_icon)
                .into(coverImageView)
        } else {
            GlideApp.with(app.applicationContext)
                .load(R.mipmap.ic_book_cover)
                .into(coverImageView)
        }
        // Authors and Genres.
        bindMultiLabelField(inflater, detailsView, getString(R.string.authorLabel),
            book.getLabels(Label.Type.Authors)
        )
        bindMultiLabelField(inflater, detailsView, getString(R.string.genreLabel),
            book.getLabels(Label.Type.Genres)
        )
        // Details.
        bindDetails(inflater, detailsView, book)
        // Remaining fields.
        val fields = mutableListOf<Triple<Int, () -> String, ((String?) -> Unit)?>>(
            Triple(R.string.publisherLabel, { book::publisher.getter()?.name ?: "" }) {
                val action = DetailsFragmentDirections.toSearchFragment(
                    Query(filters = Query.asFilter(book.firstLabel(Label.Type.Publisher)))
                )
                navController.navigate(action)
            },
            Triple(R.string.yearPublishedLabel, book::yearPublished.getter, null),
            Triple(R.string.physicalLocationLabel, { book::location.getter()?.name ?: "" } ) {
                val action = DetailsFragmentDirections.toSearchFragment(
                    Query(filters = Query.asFilter(book.firstLabel(Label.Type.Location)))
                )
                navController.navigate(action)
            },
            Triple(R.string.summaryLabel, book::summary.getter, null),
            Triple(R.string.dateAddedLabel, book::dateAdded.getter, null),
        )
        if (app.prefs.getBoolean("display_last_modified", false)) {
            fields.add(Triple(R.string.lastModifiedLabel, book::lastModified.getter, null))
        }
        if (app.prefs.getBoolean("display_book_id", false)) {
            fields.add(Triple(R.string.bookIdLabel, book::sqlId.getter, null))
        }

        fields.forEach { (labelId, getter, onClick) ->
            bindField(
                inflater,
                detailsView,
                app.applicationContext.getString(labelId),
                getter(),
                onClick
            )
        }
    }
}

private class LabelArrayAdapter(
    var dataSource: List<Label>,
    private val onClick: ((Label) -> Unit)?,
): RecyclerView.Adapter<LabelArrayAdapter.ViewHolder>() {

    class ViewHolder(
        private val binding: RecyclerviewDetailsLabelItemBinding,
        private val onClick: ((Label) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(label: Label) {
            binding.labelView.text = label.name
            if (onClick != null) {
                binding.labelView.setOnClickListener { _ ->
                    label.let { this.onClick.invoke(it) }
                }

            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RecyclerviewDetailsLabelItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onClick
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataSource[position])
    }

    override fun getItemCount(): Int {
        return dataSource.size
    }

}

