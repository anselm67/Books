package com.anselm.books.ui.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.navArgs
import com.anselm.books.databinding.FragmentPagerBinding

class PagerViewModel: ViewModel() {
    var bookIds: List<Long> = emptyList()
    var position: Int = 0
}

class PagerFragment: Fragment() {
    private var _binding: FragmentPagerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PagerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentPagerBinding.inflate(inflater, container, false)
        val safeArgs: PagerFragmentArgs by navArgs()

        if (safeArgs.bookIds != null) {
            viewModel.bookIds = safeArgs.bookIds.asList()
            viewModel.position = safeArgs.position
        }
        val adapter = BookPagerAdapter(this, viewModel.bookIds)
        binding.idPager.adapter = adapter
        binding.idPager.setCurrentItem(viewModel.position, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}