package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.hideKeyboard
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : ListFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = super.onCreateView(inflater, container, savedInstanceState)
        binding.idSearchFilters.isVisible = false
        binding.idCountView.isVisible = false
        binding.fab.isVisible = true

        handleMenu(requireActivity())

        app.repository.itemCount.observe(viewLifecycleOwner) {
            app.title = getString(R.string.book_count, it)
        }

        binding.fab.setOnClickListener {
            scanISBN()
        }
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
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun scanISBN() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13)
            .build()
        val scanner = GmsBarcodeScanning.getClient(requireContext(), options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                // Task completed successfully
                Log.d(TAG, "Found ISBN $barcode.")
                if (barcode.valueType == Barcode.TYPE_ISBN && barcode.rawValue != null) {
                    handleISBN(barcode.rawValue!!)
                }
            }.addOnCanceledListener {
                // Task canceled
                Log.d(TAG, "Scanner canceled")
            }.addOnFailureListener { e ->
                // Task failed with an exception
                Log.e(TAG, "Scanner failed.", e)
                app.toast(getString(R.string.scan_failed, e.message))
            }
    }

    private fun handleISBN(isbn: String) {
        app.loading(true)
        app.olClient.lookup(isbn, { msg: String, e: Exception? ->
            app.loading(false)
            Log.e(TAG, "$isbn: ${msg}.", e)
            app.toast("No matches found for $isbn")
        }, {
            app.loading(false)
            val activity = requireActivity()
            view?.let { myself -> activity.hideKeyboard(myself) }
            requireActivity().lifecycleScope.launch(Dispatchers.Main) {
                val action = HomeFragmentDirections.actionEditNewBook(-1, it)
                findNavController().navigate(action)
            }
        })
    }



}

