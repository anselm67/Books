package com.anselm.books.ui.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.anselm.books.databinding.FragmentPagerBinding

class PagerFragment: Fragment() {
    private lateinit var binding: FragmentPagerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentPagerBinding.inflate(inflater, container, false)
        val safeArgs: PagerFragmentArgs by navArgs()

        val adapter = BookPagerAdapter(this, safeArgs.bookIds)
        binding.idPager.adapter = adapter
        binding.idPager.setCurrentItem(safeArgs.position, false)
        return binding.root

    }
}