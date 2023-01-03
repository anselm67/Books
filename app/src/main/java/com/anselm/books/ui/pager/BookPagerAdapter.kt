package com.anselm.books.ui.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.anselm.books.ui.details.DetailsFragment
import com.anselm.books.ui.details.DetailsFragmentArgs

class BookPagerAdapter(
    val fragment: Fragment,
    private val bookIds: LongArray,
): FragmentStateAdapter(fragment) {

    override fun createFragment(position: Int): Fragment {
        val details = DetailsFragment()
        val args = DetailsFragmentArgs(bookId = bookIds[position])
        details.arguments = args.toBundle()
        return details
    }

    override fun getItemCount(): Int {
        return bookIds.size
    }

}