package com.anselm.books.ui.home

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication
import com.anselm.books.Histo
import com.anselm.books.R
import com.anselm.books.databinding.SearchDialogFragmentBinding
import com.anselm.books.databinding.SearchDialogItemLayoutBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch


class HistoAdapter(
    private val dataSource: List<Histo>,
    private val onClick: (Histo) -> Unit
): RecyclerView.Adapter<HistoAdapter.ViewHolder>() {

    class ViewHolder(private val binding: SearchDialogItemLayoutBinding,
                     private val onClick: (Histo) -> Unit)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(histo: Histo) {
            binding.idHistoValueView.text = histo.text
            binding.idHistoCountView.text = histo.count.toString()
            binding.root.setOnClickListener { _: View -> histo.let { this.onClick(it) } }
        }
    }

    override fun getItemCount(): Int {
        return dataSource.size
    }

    override fun onCreateViewHolder(parent: ViewGroup,viewType: Int): ViewHolder =
        ViewHolder(SearchDialogItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false), onClick)


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataSource[position])
    }
}

class SearchDialogFragment: BottomSheetDialogFragment() {
    private val viewModel: QueryViewModel by activityViewModels()
    private var columnName = PHYSICAL_LOCATION

    companion object {
        const val PHYSICAL_LOCATION = "physicalLocation"
        const val GENRE = "genre"
        const val PUBLISHER = "publisher"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // set custom style for bottom sheet rounded top corners
        setStyle(STYLE_NORMAL, R.style.DialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = SearchDialogFragmentBinding.inflate(inflater, container, false)
        val safeArgs: SearchDialogFragmentArgs by navArgs()

        columnName = safeArgs.columnName

        val dataSource: MutableList<Histo> = mutableListOf()
        val adapter = HistoAdapter(dataSource, onClick = { histo: Histo ->
            when (columnName) {
                PHYSICAL_LOCATION ->
                    viewModel.query.value = viewModel.query.value?.copy(location = histo.text)
                GENRE ->
                    viewModel.query.value = viewModel.query.value?.copy(genre = histo.text)
                PUBLISHER ->
                    viewModel.query.value = viewModel.query.value?.copy(publisher = histo.text)
            }
            dismiss()
        })
        binding.idHistoList.let {
            it.adapter = adapter
            it.layoutManager = LinearLayoutManager(requireActivity())
            it.addItemDecoration(DividerItemDecoration(requireActivity(), RecyclerView.VERTICAL))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            var values: List<Histo>? = null
            when (columnName) {
                PHYSICAL_LOCATION ->
                    values = BooksApplication.app.repository.getLocations()
                GENRE ->
                    values = BooksApplication.app.repository.getGenres()
                PUBLISHER ->
                    values = BooksApplication.app.repository.getPublishers()
                else
                    -> listOf<Histo>()
            }
            values?.let { dataSource.addAll(it) }
            adapter.notifyItemRangeInserted(0, values!!.size)
        }

        binding.idCancelDialog.setOnClickListener {
            dismiss()
        }

        return binding.root
    }


    private fun showFullScreenBottomSheet(bottomSheet: FrameLayout) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = Resources.getSystem().displayMetrics.heightPixels
        bottomSheet.layoutParams = layoutParams
    }

    private fun expandBottomSheet(bottomSheetBehavior: BottomSheetBehavior<FrameLayout>) {
        bottomSheetBehavior.skipCollapsed = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        //  if you wanna show the bottom sheet as full screen,
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { dialog: DialogInterface ->
            val bottomSheet = (dialog as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            showFullScreenBottomSheet(bottomSheet as FrameLayout)
            BottomSheetBehavior
                .from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
        return bottomSheetDialog
    }

}