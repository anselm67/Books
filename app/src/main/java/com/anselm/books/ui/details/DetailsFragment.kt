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
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import com.anselm.books.databinding.DetailsDetailsLayoutBinding
import com.anselm.books.databinding.DetailsFieldLayoutBinding
import com.anselm.books.databinding.DetailsMultiLabelLayoutBinding
import com.anselm.books.databinding.FragmentDetailsBinding
import com.anselm.books.databinding.RecyclerviewDetailsLabelItemBinding
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

        val repository = BooksApplication.app.repository
        val safeArgs: DetailsFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            val book: Book = repository.load(safeArgs.bookId, decorate = true)
            bookId = book.id
            binding.bind(inflater, book)
        }

        handleMenu(requireActivity())

        return binding.root
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
                    val action = DetailsFragmentDirections.actionDetailsFragmentToEditFragment(bookId)
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
        arrayOf<Triple<() -> String, View, TextView>>(
            Triple(
                book::isbn.getter,
                binding.isbnContainerView,
                binding.isbnView
            ),
            Triple(
                book::language.getter,
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
                val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                    Query(filters = mutableListOf(Query.Filter(it))))
                navController.navigate(action)
            }
        )
        binding.labelView.text = label
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
            book.getLabels(Label.Type.Authors)
        )
        bindMultiLabelField(inflater, detailsView, getString(R.string.genreLabel),
            book.getLabels(Label.Type.Genres)
        )
        // Details.
        bindDetails(inflater, detailsView, book)
        // Remaining fields.
        val fields = mutableListOf<Triple<Int, () -> String, ((String?) -> Unit)?>>(
            Triple(R.string.publisherLabel, book::publisher.getter) {
                val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
                    Query(filters = Query.asFilter(book.firstLabel(Label.Type.Publisher)))
                )
                navController.navigate(action)
            },
            Triple(R.string.yearPublishedLabel, book::yearPublished.getter, null),
            Triple(R.string.physicalLocationLabel, book::location.getter) {
                val action = DetailsFragmentDirections.actionDetailsFragmentToSearchFragment(
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

