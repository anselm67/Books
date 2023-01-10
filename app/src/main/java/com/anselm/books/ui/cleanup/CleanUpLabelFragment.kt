package com.anselm.books.ui.cleanup

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Label
import com.anselm.books.databinding.FragmentCleanupLabelBinding
import com.anselm.books.databinding.RecyclerviewLabelCleanupItemBinding
import com.anselm.books.hideKeyboard
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.launch

class CleanUpLabelFragment: BookFragment() {
    private var _binding: FragmentCleanupLabelBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: LabelCleanupArrayAdapter
    private lateinit var type: Label.Type

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentCleanupLabelBinding.inflate(inflater, container, false)

        val safeArgs: CleanUpLabelFragmentArgs by navArgs()
        type = safeArgs.type

        viewLifecycleOwner.lifecycleScope.launch {
            adapter = LabelCleanupArrayAdapter(app.repository.getLabels(type).toMutableList())
            binding.idLabelRecyclerView.adapter = adapter
            binding.idLabelRecyclerView.layoutManager = LinearLayoutManager(
                binding.idLabelRecyclerView.context
            )
            binding.idLabelRecyclerView.addItemDecoration(
                DividerItemDecoration(requireActivity(), RecyclerView.VERTICAL))
        }

        binding.idAutoComplete.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

            override fun afterTextChanged(s: Editable?) {
                val labelQuery = s.toString()
                if (labelQuery.isEmpty()) {
                    loadLabels()
                } else {
                    loadLabels(s.toString() + '*')
                }
            }
        })
        super.handleMenu(emptyList())
        return binding.root
    }

    private fun loadLabels(labelQuery: String? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val labels = app.repository.searchLabels(type, labelQuery)
            adapter.updateData(labels)
        }
    }
}

private class LabelCleanupArrayAdapter(
    val labels: MutableList<Label>
): RecyclerView.Adapter<LabelCleanupArrayAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: RecyclerviewLabelCleanupItemBinding,
    ): RecyclerView.ViewHolder(binding.root) {

        fun bind(label: Label) {
            binding.idLabelText.setText(label.name)
            binding.idDeleteLabel.setOnClickListener {
                removeAt(bindingAdapterPosition)
            }
            val editBackground = binding.idLabelText.background
            binding.idLabelText.background = ColorDrawable(Color.WHITE)
            binding.idEditLabel.setOnClickListener {
                binding.idLabelText.background = editBackground
                binding.idLabelText.focusable = View.NOT_FOCUSABLE
                binding.idLabelText.isCursorVisible = true
                binding.idLabelText.requestFocus()
            }
            binding.idLabelText.setOnEditorActionListener(object: OnEditorActionListener {
                override fun onEditorAction(v: TextView?,actionId: Int,event: KeyEvent?): Boolean {
                    return if (actionId != EditorInfo.IME_ACTION_DONE) {
                        return false
                    } else {
                        binding.idLabelText.background = ColorDrawable(Color.WHITE)
                        binding.idLabelText.focusable = View.FOCUSABLE_AUTO
                        binding.idLabelText.isCursorVisible = false
                        app.hideKeyboard(binding.root)
                        true
                    }
                }
            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RecyclerviewLabelCleanupItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(labels[position])
    }

    override fun getItemCount(): Int {
        return labels.size
    }

    private fun removeAt(position: Int) {
        val label = labels[position]
        app.applicationScope.launch {
            app.repository.deleteLabel(label)
            labels.removeAt(position)
            app.postOnUiThread { notifyItemRemoved(position) }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newLabels: List<Label>) {
        labels.clear()
        labels.addAll(newLabels)
        notifyDataSetChanged()
    }
}

