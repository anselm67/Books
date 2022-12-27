package com.anselm.books.ui.gallery

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentGalleryBinding
import com.anselm.books.hideKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GalleryFragment : Fragment() {
    private val app = BooksApplication.app
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val app = BooksApplication.app
        val importExport = app.importExport
        val getContent =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                val context = app.applicationContext
                Log.d(TAG, "Opening file $uri")
                if (uri == null) {
                    Log.d(TAG, "No file selected, nothing to import")
                    app.toast(R.string.select_import_file_prompt)
                } else {
                    var counts: Pair<Int, Int> = Pair(-1, -1)
                    app.loading(true)
                    app.applicationScope.launch {
                        counts = importExport.importZipFile(uri)
                    }.invokeOnCompletion {
                        // We're running on the application lifecycle scope, so this view that we're
                        // launching from might be done by the time we get here, protect against that.
                        app.toast(context.getString(R.string.import_status, counts.first, counts.second))
                        app.loading(false)
                    }
                }
            }

        binding.importButton.setOnClickListener {
            getContent.launch("*/*")
        }

        binding.lookupISBNButton.setOnClickListener {
            val isbn = binding.idISBNText.text.toString().trim()
            view?.let { myself -> activity?.hideKeyboard(myself) }
            handleISBN(isbn)
        }

        handleMenu(requireActivity())
        return root
    }



    private fun handleISBN(isbn: String) {
        app.loading(true)
        app.olClient.lookup(isbn, { msg: String, e: Exception? ->
            app.loading(false)
            Log.e(TAG, "$isbn: ${msg}.", e)
            app.toast("No matches found for $isbn")
            binding.idISBNText.setText("")
        }, {
            app.loading(false)
            requireActivity().lifecycleScope.launch(Dispatchers.Main) {
                val action = GalleryFragmentDirections.addBook(-1, it)
                findNavController().navigate(action)
            }
        })
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.findItem(R.id.idEditBook)?.isVisible = false
                menu.findItem(R.id.idSaveBook)?.isVisible = false
                menu.findItem(R.id.idSearchView)?.isVisible = false
                menu.findItem(R.id.idGotoSearchView)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /*
    private fun checkAvailability() {
        Log.d(TAG, "checkAvailability")
        val context = requireContext()
        val moduleInstallClient = ModuleInstall.getClient(context)
        val optionalModuleApi = GmsBarcodeScanning.getClient(context)
        moduleInstallClient.areModulesAvailable(optionalModuleApi).addOnSuccessListener {
            if (it.areModulesAvailable()) {
                Log.d(TAG, "Module is available")
            } else {
                Log.d(TAG, "Module isn't available.")
                //moduleInstallClient.deferredInstall(optionalModuleApi)

            }
        }.addOnFailureListener {
            Log.e(TAG, "Install failed.", it)
        }
    } */

    /*
     * Keeping this around it'll come handy in due time.

val writeDocument =
    registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            Log.d(TAG, "Failed to select directory tree.")
        } else {
            Log.d(TAG, "Opening directory ${uri}")
            activity?.lifecycleScope?.launch {
                try {
                    context?.contentResolver?.openFileDescriptor(uri, "w")?.use {
                        FileOutputStream(it.fileDescriptor).use {
                            it.write(("Overwritten at ${System.currentTimeMillis()}\\n").toByteArray())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write to file {uri}")
                }
            }
        }
    } */

}

