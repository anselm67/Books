package com.anselm.books.ui.widgets

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.database.Label
import com.anselm.books.databinding.RecyclerviewLabelItemBinding

class LabelArrayAdapter(
    var dataSource: MutableList<Label>,
    private val onClick: ((Label) -> Unit)?,
    private val onChange: ((List<Label>) -> Unit)? = null
): RecyclerView.Adapter<LabelArrayAdapter.ViewHolder>() {

    class ViewHolder(
        private val binding: RecyclerviewLabelItemBinding,
        private val onClick: ((Label) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(label: Label) {
            binding.labelView.text = label.name
            if (onClick != null) {
                binding.labelView.setOnClickListener { _ ->
                    label.let { this.onClick.invoke(it) }
                }

            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RecyclerviewLabelItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onClick
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataSource[position])
    }

    override fun getItemCount(): Int {
        return dataSource.size
    }

    val differ = AsyncListDiffer(
        this, object : DiffUtil.ItemCallback<Label>() {
            override fun areItemsTheSame(oldItem: Label, newItem: Label) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: Label, newItem: Label) =
                oldItem == newItem
        }
    )

    fun moveItem(from: Int, to: Int) {
        val fromLocation = dataSource[from]
        dataSource[from] = dataSource[to]
        dataSource[to] = fromLocation
        differ.submitList(dataSource)
        onChange?.invoke(dataSource)
    }

}

class DnDList(
    val list: RecyclerView,
    labels: MutableList<Label>,
    onClick: ((Label) -> Unit)? = null,
    onChange: ((List<Label>) -> Unit)? = null,
) {

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {

            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                val adapter = (recyclerView.adapter as LabelArrayAdapter)
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.moveItem(from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f
            }
        }

        ItemTouchHelper(simpleItemTouchCallback)
    }

    init {
        val adapter = LabelArrayAdapter(labels, onClick, onChange)
        itemTouchHelper.attachToRecyclerView(list)
        adapter.differ.submitList(labels)
        list.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setLabels(labels: MutableList<Label>) {
        val adapter = (list.adapter as LabelArrayAdapter)
        adapter.dataSource = labels
        adapter.notifyDataSetChanged()
    }

    fun getLabels(): List<Label> {
        return (list.adapter as LabelArrayAdapter).dataSource
    }

    fun addLabel(label: Label): Boolean {
        val labels = (list.adapter as LabelArrayAdapter).dataSource
        return if ( ! labels.contains(label) ) {
            val adapter = (list.adapter as LabelArrayAdapter)
            labels.add(label)
            adapter.notifyItemInserted(labels.size - 1)
            true
        } else {
            false
        }
    }

    fun setLabel(label: Label): Boolean {
        val labels = (list.adapter as LabelArrayAdapter).dataSource
        return if (labels == listOf(label)) {
            false
        } else {
            setLabels(mutableListOf(label))
            true
        }
    }
}