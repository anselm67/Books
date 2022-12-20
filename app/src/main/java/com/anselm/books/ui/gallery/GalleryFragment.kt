package com.anselm.books.ui.gallery

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.anselm.books.BooksApplication
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentGalleryBinding
import com.anselm.books.hideKeyboard
import com.anselm.books.openlibrary.OpenLibraryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val olClient = OpenLibraryClient()

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
                Log.d(TAG, "Opening file $uri")
                if (uri == null) {
                    Log.d(TAG, "No field selected, nothing to import")
                } else {
                    var counts: Pair<Int, Int> = Pair(-1, -1)
                    app.loading(true)
                    app.applicationScope.launch {
                        counts = importExport.importZipFile(uri)
                    }.invokeOnCompletion {
                        // We're running on the application lifecycle scope, so this view that we're
                        // launching from might be done by the time we get here, protect against that.
                        app.toast("Imported ${counts.first} books and ${counts.second} images.")
                        app.loading(false)
                    }
                }
            }

        binding.importButton.setOnClickListener {
            getContent.launch("*/*")
        }

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

        binding.lookupISBNButton.setOnClickListener {
            app.loading(true)
            val isbn = binding.idISBNText.text.toString().trim()
            olClient.lookup(isbn, { msg: String, e: Exception? ->
                app.loading(false)
                Log.e(TAG, "$isbn: ${msg}.", e)
                app.toast("No matches found for $isbn")
                binding.idISBNText.setText("")
            }, {
                app.loading(false)
                val activity = requireActivity()
                view?.let { myself -> activity.hideKeyboard(myself) }
                requireActivity().lifecycleScope.launch(Dispatchers.Main) {
                    val action = GalleryFragmentDirections.addBook(-1, it)
                    findNavController().navigate(action)
                }
            })
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}

