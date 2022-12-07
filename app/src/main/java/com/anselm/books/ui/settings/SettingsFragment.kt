package com.anselm.books.ui.settings

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.bumptech.glide.Glide

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        // Clears the Glide cache when requested.
        findPreference<Preference>("glide_clear_cache")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                Log.i(TAG, "Clear glide cache directory.")
                (activity?.application as BooksApplication).executor.execute {
                    Glide.get(requireContext()).clearDiskCache()
                }
                Toast.makeText(
                    activity?.applicationContext,
                    R.string.glide_cache_cleared,
                    Toast.LENGTH_SHORT).show()
                true
            }

    }

}