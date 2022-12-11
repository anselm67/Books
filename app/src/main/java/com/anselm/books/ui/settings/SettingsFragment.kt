package com.anselm.books.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        // Clears the Glide cache when requested.
        findPreference<Preference>("glide_clear_cache")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                Log.i(TAG, "Clear glide cache directory.")
                BooksApplication.app.applicationScope.launch {
                    Glide.get(requireContext()).clearDiskCache()
                }
                BooksApplication.app.toast(R.string.glide_cache_cleared)
                true
            }
        handleMenu(requireActivity())
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.findItem(R.id.idSearchView)?.isVisible = false
                menu.findItem(R.id.idEditBook)?.isVisible = false
                menu.findItem(R.id.idSaveBook)?.isVisible = false
                menu.findItem(R.id.idGotoSearchView)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        })
    }

}