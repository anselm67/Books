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

            binding.idBookHeader.text = getString(
                R.string.book_count,
                app.repository.getTotalCount())


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

            val types = app.repository.getLabelTypeCounts()
            // Authors
            binding.idCleanupAuthors.text = getString(
                R.string.authors_cleanup,
                types.firstOrNull { it.type == Label.Type.Authors }?.count ?: 0
            )
            binding.idCleanupAuthors.setOnClickListener {
                val action = CleanUpFragmentDirections.toCleanupLabelFragment(Label.Type.Authors)
                findNavController().navigate(action)
            }
            // Genres
            binding.idCleanupGenres.text = getString(
                R.string.genres_cleanup,
                types.firstOrNull { it.type == Label.Type.Genres }?.count ?: 0
            )
            binding.idCleanupGenres.setOnClickListener {
                val action = CleanUpFragmentDirections.toCleanupLabelFragment(Label.Type.Genres)
                findNavController().navigate(action)
            }
            // Publishers
            binding.idCleanupPublishers.text = getString(
                R.string.publishers_cleanup,
                types.firstOrNull { it.type == Label.Type.Publisher }?.count ?: 0
            )
            binding.idCleanupPublishers.setOnClickListener {
                val action = CleanUpFragmentDirections.toCleanupLabelFragment(Label.Type.Publisher)
                findNavController().navigate(action)
            }
            // Languages
            binding.idCleanupLanguages.text = getString(
                R.string.languages_cleanup,
                types.firstOrNull { it.type == Label.Type.Language }?.count ?: 0
            )
            binding.idCleanupLanguages.setOnClickListener {
                val action = CleanUpFragmentDirections.toCleanupLabelFragment(Label.Type.Language)
                findNavController().navigate(action)
            }
            // Locations
            binding.idCleanupLocations.text = getString(
                R.string.locations_cleanup,
                types.firstOrNull { it.type == Label.Type.Location }?.count ?: 0
            )
            binding.idCleanupLocations.setOnClickListener {
                val action = CleanUpFragmentDirections.toCleanupLabelFragment(Label.Type.Location)
                findNavController().navigate(action)
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


            binding.idStatsView.text = text


        }

        return binding.root
    }
}