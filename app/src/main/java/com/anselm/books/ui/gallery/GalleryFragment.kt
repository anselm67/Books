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
import org.json.JSONTokener

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
                Log.d(TAG, "No fie selected, nothing to import")
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

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun populate(repository: BookRepository, obj: Any) {
        if (obj is JSONArray) {
            Log.d(TAG, "Populating database...")
            repository.deleteAll()
            (0 until obj.length()).forEach { i ->
                val book = Book(obj.getJSONObject(i))
                repository.insert(book)
            }
            Log.d(TAG, "Created ${obj.length()} books.")
            repository.invalidate()
        }
    }
}

