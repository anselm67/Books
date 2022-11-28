package com.anselm.books.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.BookListAdapter
import com.anselm.books.BookViewModelFactory
import com.anselm.books.BooksApplication
import com.anselm.books.databinding.FragmentHomeBinding
import com.anselm.books.BookViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val recyclerView = binding.recyclerview
        val adapter = BookListAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(root.context)

        val bookViewModel: BookViewModel by viewModels {
            BookViewModelFactory((activity?.application as BooksApplication).repository)
        }
        bookViewModel.allBooks.observe(viewLifecycleOwner) { books ->
            books?.let { adapter.submitList(books) }
        }


        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}