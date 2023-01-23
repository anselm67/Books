package com.anselm.books.ui.pager

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.databinding.FragmentPagerBinding

class PagerViewModel: ViewModel() {
    var bookIds = emptyList<Long>().toMutableList()
    var position: Int = 0
}

class PagerFragment: Fragment() {
    private var _binding: FragmentPagerBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: BookPagerAdapter
    private lateinit var viewModel: PagerViewModel

    private fun deleteCurrentAndContinue() {
        val position = viewModel.position
        if (position >= 0 && position < viewModel.bookIds.size) {
            viewModel.bookIds.removeAt(position)
            binding.idPager.adapter?.notifyItemRemoved(position)
        }
        var newPosition = position
        if (newPosition >= viewModel.bookIds.size) {
            if (viewModel.bookIds.isEmpty()) {
                findNavController().popBackStack()
            } else {
                newPosition = viewModel.bookIds.size - 1
            }
        }
        binding.idPager.currentItem = newPosition
        updatePosition()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object: OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val savedStateHandle =  findNavController()
                            .previousBackStackEntry
                            ?.savedStateHandle
                    if (savedStateHandle?.contains("bookDeleted") == true) {
                        deleteCurrentAndContinue()
                        savedStateHandle.remove("bookDeleted")
                    } else {
                        findNavController().popBackStack()
                    }
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentPagerBinding.inflate(inflater, container, false)
        val safeArgs: PagerFragmentArgs by navArgs()

        val isModelInitialized = ::viewModel.isInitialized
        viewModel = ViewModelProvider(this)[PagerViewModel::class.java]
        if ( ! isModelInitialized ) {
            viewModel.bookIds = safeArgs.bookIds.toMutableList()
            viewModel.position = safeArgs.position
        }
        adapter = BookPagerAdapter(this, viewModel.bookIds)
        binding.idPager.adapter = adapter
        binding.idPager.registerOnPageChangeCallback(object: OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.position = position
                updatePosition()
            }
        })
        binding.idPager.setCurrentItem(viewModel.position, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun updatePosition() {
        val position = viewModel.position
        val itemCount = adapter.itemCount
        app.title = getString(R.string.pager_position, position + 1, itemCount)
    }
}