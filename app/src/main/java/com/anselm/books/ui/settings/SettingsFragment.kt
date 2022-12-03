package com.anselm.books.ui.settings

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        // Handles the Picasso clear cache command ...
        findPreference<Preference>("picasso_clear_cache")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                Log.i(TAG, "Clear picasso cache directory.")
                (activity?.application as BooksApplication).clearPicassoCache()
                Toast.makeText(
                    activity?.applicationContext,
                    R.string.picasso_cache_cleared,
                    Toast.LENGTH_SHORT).show()
                true
            }

    }

}