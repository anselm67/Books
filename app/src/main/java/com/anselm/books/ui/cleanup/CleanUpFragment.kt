package com.anselm.books.ui.cleanup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.anselm.books.R
import com.anselm.books.database.Label
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

        val text = StringBuilder()

        viewLifecycleOwner.lifecycleScope.launch {
            var count = app.repository.deleteUnusedLabels()
            text.append("Deleted $count unused labels.\n")

            // Books hygiene.
            count = app.repository.getDuplicateBooksCount()
            if (count > 0) {
                binding.idDuplicateText.visibility = View.VISIBLE
                binding.idDuplicateText.text = getString(R.string.duplicate_books_cleanup, count)
                binding.idDuplicateText.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val dupes = app.repository.getDuplicateBooksIds()
                        val action = CleanUpFragmentDirections.toPagerFragment(
                            dupes.toLongArray(), 0)
                        findNavController().navigate(action)
                    }
                }
            }
            // Books without cover images.
            count = app.repository.getWithoutCoverBooksCount()
            if (count > 0) {
                binding.idWithoutCoverText.visibility = View.VISIBLE
                binding.idWithoutCoverText.text = getString(
                    R.string.without_cover_books_cleanup, count
                )
                binding.idWithoutCoverText.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val withoutCovers = app.repository.getWithoutCoverBookIds()
                        val action = CleanUpFragmentDirections.toPagerFragment(
                            withoutCovers.toLongArray(), 0)
                        findNavController().navigate(action)
                    }
                }

            }

            // Books without certain types of labels.
            count = app.repository.getBooksWithoutLabelCount(Label.Type.Authors)
            text.append("$count without author.\n")
            count = app.repository.getBooksWithoutLabelCount(Label.Type.Genres)
            text.append("$count without genres.\n")
            count = app.repository.getBooksWithoutLabelCount(Label.Type.Location)
            text.append("$count without a physical location.\n")
            count = app.repository.getBooksWithoutLabelCount(Label.Type.Language)
            text.append("$count without a language.\n")


            // Label management: count of different values.
            val types = app.repository.getLabelTypeCounts()
            types.map {
                text.append(it.count)
                when (it.type) {
                    Label.Type.Authors -> text.append(" authors")
                    Label.Type.Genres -> text.append(" genres")
                    Label.Type.Location -> text.append(" locations")
                    Label.Type.Publisher -> text.append(" publishers")
                    Label.Type.Language -> text.append(" languages")
                }
                text.append('\n')
            }
            binding.idStatsView.text = text


        }

        return binding.root
    }
}