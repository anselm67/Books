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
import com.anselm.books.Book
import com.anselm.books.BookRepository
import com.anselm.books.BooksApplication
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentGalleryBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
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

        val repository = ((activity?.application) as BooksApplication).repository
        val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            Log.d(TAG, "Opening file $uri")
            if (uri == null) {
                Log.d(TAG, "No fiel selected, nothing to import")
            } else {
                var text: String?
                context?.contentResolver?.openInputStream(uri).use {
                    text = it?.bufferedReader()?.readText()
                }
                Log.d(TAG, "This file has ${text?.length} characters.")
                // Parse the json stream
                if (text != null) {
                    val tok = JSONTokener(text)
                    val obj = tok.nextValue()
                    Log.d(TAG, "Type: ${obj::class.qualifiedName}")
                    activity?.lifecycleScope?.launch {
                        populate(repository, obj)
                    }
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

    private suspend fun populate(repository: BookRepository, rootObject: Any) {
        if (rootObject is JSONObject) {
            val books = rootObject.get("books")
            if (books is JSONArray) {
                Log.d(TAG, "Populating database...")
                repository.deleteAll()
                (0 until books.length()).forEach { i ->
                    val book = Book(books.getJSONObject(i))
                    repository.insert(book)
                }
                Log.d(TAG, "Created ${books.length()} books.")
                repository.invalidate()
            } else {
                Log.d(TAG, "No 'books' list in root object, no books inserted.")
            }
        } else {
            Log.d(TAG, "Invalid root object type ${rootObject::class.qualifiedName}" +
                    " no books inserted.")
        }
    }
}

