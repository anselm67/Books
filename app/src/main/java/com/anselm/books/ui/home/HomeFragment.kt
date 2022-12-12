package com.anselm.books.ui.home

import android.os.Bundle
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.anselm.books.R

class HomeFragment : ListFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = super.onCreateView(inflater, container, savedInstanceState)
        binding.idSearchFilters.isVisible = false
        handleMenu(requireActivity())
        return root
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.findItem(R.id.idEditBook)?.isVisible = false
                menu.findItem(R.id.idSaveBook)?.isVisible = false
                menu.findItem(R.id.idSearchView)?.isVisible = false
                menu.findItem(R.id.idGotoSearchView)?.isVisible = true
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return if (menuItem.itemId == R.id.idGotoSearchView) {
                    val action = HomeFragmentDirections.actionHomeFragmentToSearchFragment()
                    findNavController().navigate(action)
                    true
                } else {
                    false
                }
            }
        })
    }
}

