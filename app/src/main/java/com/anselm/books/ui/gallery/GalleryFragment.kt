package com.anselm.books.ui.gallery

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.anselm.books.BooksApplication
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentGalleryBinding
import kotlinx.coroutines.launch
import java.io.FileOutputStream

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val importExport = ((activity?.application) as BooksApplication).importExport
        val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            Log.d(TAG, "Opening file $uri")
            if (uri == null) {
                Log.d(TAG, "No field selected, nothing to import")
            } else {
                activity?.lifecycleScope?.launch {
                    val count = importExport.importJsonFile(uri)
                    Toast.makeText(
                        activity?.applicationContext,
                        "Imported $count books.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.importButton.setOnClickListener {
            Log.d(TAG, "Button clicked.")
            getContent.launch("*/*")
        }

        val writeDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
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
        }

        binding.writeButton.setOnClickListener {
            Log.d(TAG, "Processing directory button.")
            writeDocument.launch("Foo.txt")
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}

