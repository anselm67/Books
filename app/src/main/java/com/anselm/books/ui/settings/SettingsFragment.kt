package com.anselm.books.ui.settings

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.ui.widgets.BookFragment
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        handleMenu(requireActivity())

        return root
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Clears the Glide cache when requested.
        findPreference<Preference>("glide_clear_cache")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                Log.i(TAG, "Clear glide cache directory.")
                BooksApplication.app.applicationScope.launch {
                    Glide.get(requireContext()).clearDiskCache()
                }
                BooksApplication.app.toast(R.string.glide_cache_cleared)
                true
            }

        // Sets up for import.
        val importer = setupImport()
        findPreference<Preference>("import_preference")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                importer.launch("*/*")
                true
            }

        // Sets up for export.
        val exporter = setupExport()
        findPreference<Preference>("export_preference")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                exporter.launch("books.zip")
                true
            }

    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                BookFragment.allItemIds.forEach {
                    menu.findItem(it).isVisible = false
                }
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupImport(): ActivityResultLauncher<String> {
        val app = BooksApplication.app
        val importExport = app.importExport
        return registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                val context = app.applicationContext
                Log.d(TAG, "Opening file $uri")
                if (uri == null) {
                    Log.d(TAG, "No file selected, nothing to import")
                    app.toast(R.string.select_import_file_prompt)
                } else {
                    var counts: Pair<Int, Int> = Pair(-1, -1)
                    var msg: String? = null
                    app.loading(true)
                    app.applicationScope.launch {
                        try {
                            counts = importExport.importZipFile(uri)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to import books.", e)
                            msg = e.message
                        }
                    }.invokeOnCompletion {
                        // We're running on the application lifecycle scope, so this view that we're
                        // launching from might be done by the time we get here, protect against that.
                        val text = if (msg != null) {
                            context.getString(R.string.import_failed, msg)
                        } else {
                            context.getString(R.string.import_status, counts.first, counts.second)
                        }
                        app.loading(false)
                        app.toast(text)
                    }
                }
            }
    }

    private fun setupExport(): ActivityResultLauncher<String> {
        val app = BooksApplication.app
        val context = app.applicationContext
        val importExport = app.importExport
        return registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            if (uri == null) {
                Log.d(TAG, "Failed to select directory tree.")
                app.toast("Select a file to export to.")
            } else {
                Log.d(TAG, "Opening directory $uri")
                app.loading(true, "$TAG.Export")
                var count = 0
                var msg: String? = null
                app.applicationScope.launch {
                    try {
                        count = importExport.exportZipFile(uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Export to $uri failed.", e)
                        msg = e.TAG
                    }
                }.invokeOnCompletion {
                    val text = if (msg != null) {
                        context.getString(R.string.export_failed, msg)
                    } else {
                        context.getString(R.string.export_status, count)
                    }
                    app.loading(false, "$TAG.Export")
                    app.toast(text)
                }
            }
        }
    }
}

