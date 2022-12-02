package com.anselm.books.ui.slideshow

import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anselm.books.MainActivity
import com.anselm.books.R
import com.anselm.books.TAG

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        // Handles the Picasso clear cache command ...
        findPreference<Preference>("picasso_clear_cache")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                Log.i(TAG, "Clear picasso cache directory.")
                true
            }

        val supportActionBar = (activity as MainActivity).supportActionBar
        //supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

    }

}