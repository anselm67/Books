package com.anselm.books.ui.settings

import android.os.Bundle
import android.util.Log
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

    }

}