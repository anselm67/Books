package com.anselm.books.ui.cleanup

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Label
import com.anselm.books.databinding.FragmentCleanupLabelBinding
import com.anselm.books.databinding.RecyclerviewLabelCleanupItemBinding
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.launch

class CleanUpLabelFragment: BookFragment() {
    private var _binding: FragmentCleanupLabelBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: LabelCleanupArrayAdapter
    private lateinit var type: Label.Type

    // https://stackoverflow.com/questions/70226403/merge-items-in-recycler-view-when-dragged-dropped-on-one-another-in-android
    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            var target: RecyclerView.ViewHolder? = null
            var moving: RecyclerView.ViewHolder? = null

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                this.target?.let { (it as LabelCleanupArrayAdapter.ViewHolder).offTarget() }
                this.target = target
                this.moving = viewHolder
                (target as LabelCleanupArrayAdapter.ViewHolder).onTarget()
                Log.d(TAG, "onMove ${this.target == this.moving}")
                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                Log.d(TAG, "clearView")
                target?.let { (it as LabelCleanupArrayAdapter.ViewHolder).offTarget() }
                promptForMerge(
                    moving?.bindingAdapterPosition ?: -1,
                    target?.bindingAdapterPosition ?: -1,
                )
                target = null
                moving = null
                viewHolder.itemView.alpha = 1f
            }
        }
        ItemTouchHelper(simpleItemTouchCallback)
    }

    private fun promptForMerge(from: Int, to: Int) {
        val fromLabel = adapter.label(from)
        val intoLabel = adapter.label(to)
        if (fromLabel == null || intoLabel == null) {
            return
        }
        val builder = AlertDialog.Builder(requireActivity())
        builder.setMessage(getString(R.string.merge_labels_prompt, fromLabel.name, intoLabel.name))
            .setPositiveButton(R.string.yes) { _, _ -> adapter.merge(from, to) }
            .setNegativeButton(R.string.no) { _, _ -> }
            .show()
    }

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
        itemTouchHelper.attachToRecyclerView(binding.idLabelRecyclerView)
        binding.idSearchLabel.addTextChangedListener(object: TextWatcher {
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
            binding.idLabelText.text = label.name
            binding.idDeleteLabel.setOnClickListener {
                removeAt(bindingAdapterPosition)
            }
        }

        fun onTarget() {
            binding.idLabelCleanupContainer.background =
                ResourcesCompat.getDrawable(
                    app.resources, R.drawable.textview_border, null)
        }

        fun offTarget() {
            binding.idLabelCleanupContainer.background = null
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

    fun label(position: Int): Label? {
        return if (position >= 0 && position < labels.size) {
            labels[position]
        } else {
            null
        }
    }

    fun merge(from: Int, to: Int) {
        val fromLabel = label(from)
        val intoLabel = label(to)
        if (fromLabel != null && intoLabel != null) {
            app.applicationScope.launch {
                app.repository.mergeLabels(fromLabel, intoLabel)
            }
            removeAt(from)
        }
    }
}

