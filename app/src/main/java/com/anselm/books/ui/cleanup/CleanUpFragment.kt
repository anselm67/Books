package com.anselm.books.ui.cleanup

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Label
import com.anselm.books.databinding.CleanupHeaderLayoutBinding
import com.anselm.books.databinding.CleanupItemLayoutBinding
import com.anselm.books.databinding.FragmentCleanupBinding
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.launch

class CleanUpFragment: BookFragment() {
    private var _binding: FragmentCleanupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentCleanupBinding.inflate(inflater, container, false)


        viewLifecycleOwner.lifecycleScope.launch {
            val count = app.repository.deleteUnusedLabels()
            Log.d(TAG, "Deleted $count unused labels.")
            bookSection(inflater, binding.idStatsContainer)
            labelSection(inflater, binding.idStatsContainer)
        }

        super.handleMenu(emptyList())
        return binding.root
    }

    private suspend fun bookSection(inflater: LayoutInflater, container: ViewGroup) {
        // Book section.
        container.addView(header(
            inflater,
            container,
            getString(R.string.book_count,app.repository.getTotalCount()),
        ))
        // Duplicate books.
        var count = app.repository.getDuplicateBookCount()
        container.addView(bookItem(
            inflater,
            container,
            getString(R.string.duplicate_books_cleanup, count)) {
            app.repository.getDuplicateBookIds()
        })
        // Books without cover images.
        count = app.repository.getWithoutCoverBookCount()
        container.addView(bookItem(
            inflater, container,
            getString(R.string.without_cover_books_cleanup, count)) {
               app.repository.getWithoutCoverBookIds()
        })
        // Books without certain label type.
        count = app.repository.getWithoutLabelBookCount(Label.Type.Authors)
        container.addView(bookItem(
            inflater, container,
            getString(R.string.without_authors_cleanup, count)) {
            app.repository.getWithoutLabelBookIds(Label.Type.Authors)
        })
        count = app.repository.getWithoutLabelBookCount(Label.Type.Genres)
        container.addView(bookItem(
            inflater, container,
            getString(R.string.without_genres_cleanup, count)) {
            app.repository.getWithoutLabelBookIds(Label.Type.Genres)
        })
        count = app.repository.getWithoutLabelBookCount(Label.Type.Location)
        container.addView(bookItem(
            inflater, container,
            getString(R.string.without_locations_cleanup, count)) {
            app.repository.getWithoutLabelBookIds(Label.Type.Location)
        })
        count = app.repository.getWithoutLabelBookCount(Label.Type.Language)
        container.addView(bookItem(
            inflater, container,
            getString(R.string.without_languages_cleanup, count)) {
            app.repository.getWithoutLabelBookIds(Label.Type.Language)
        })
    }

    private suspend fun labelSection(inflater: LayoutInflater, container: ViewGroup) {
        container.addView(header(inflater, container,getString(R.string.labels_cleanup_header)))
        val types = app.repository.getLabelTypeCounts()
        listOf(
            Pair(R.string.authors_cleanup, Label.Type.Authors),
            Pair(R.string.genres_cleanup, Label.Type.Genres),
            Pair(R.string.publishers_cleanup, Label.Type.Publisher),
            Pair(R.string.languages_cleanup, Label.Type.Language),
            Pair(R.string.locations_cleanup, Label.Type.Location),
        ).map { (stringId, type) ->
            container.addView(labelItem(
                inflater, container,
                getString(stringId,
                    types.firstOrNull { it.type == type }?.count ?: 0),
                type,
            ))
        }
    }

    private fun header( inflater: LayoutInflater, container: ViewGroup, title: String,): View {
        val header = CleanupHeaderLayoutBinding.inflate(inflater, container, false)
        header.idHeader.text = title
        return header.root
    }

    private fun bookItem(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        getItemIds: suspend () -> List<Long>,
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        item.idItemText.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val action = CleanUpFragmentDirections.toPagerFragment(
                    getItemIds().toLongArray(), 0
                )
                findNavController().navigate(action)
            }
        }
        return item.root
    }

    private fun labelItem(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        labelType: Label.Type
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        item.idItemText.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val action = CleanUpFragmentDirections.toCleanupLabelFragment(labelType)
                findNavController().navigate(action)
            }
        }
        return item.root
    }

}