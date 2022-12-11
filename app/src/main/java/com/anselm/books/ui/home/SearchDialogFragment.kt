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
import kotlin.math.min


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
    private lateinit var adapter: HistoAdapter
    private val dataSource: MutableList<Histo> = mutableListOf()

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

        // Selects the column on which to filter.
        val safeArgs: SearchDialogFragmentArgs by navArgs()
        columnName = safeArgs.columnName

        // Prepares the recycler view and kicks the values fetch.
        adapter = HistoAdapter(dataSource, onClick = { h: Histo -> selectHisto(h)  })
        binding.idHistoList.let {
            it.adapter = adapter
            it.layoutManager = LinearLayoutManager(requireActivity())
            it.addItemDecoration(DividerItemDecoration(requireActivity(), RecyclerView.VERTICAL))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            loadValues()
            adjustDialogHeight(binding.idHistoList)
        }

        // Handles cancellation without value selected.
        binding.idCancelDialog.setOnClickListener {
            dismiss()
        }

        return binding.root
    }

    private suspend fun loadValues() {
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

    /**
     * Adjusts the height of the expanded dialog so it matches the [view].
     */
    private fun adjustDialogHeight(view: View) {
        view.post {
            if (view.isLaidOut) {
                sizeDialog(view.height)
            }
        }
    }

    /**
     * Selects the given [histo] value for the filter, and dismisses the dialog.
     */
    private fun selectHisto(histo: Histo) {
        when (columnName) {
            PHYSICAL_LOCATION ->
                viewModel.query.value = viewModel.query.value?.copy(location = histo.text)
            GENRE ->
                viewModel.query.value = viewModel.query.value?.copy(genre = histo.text)
            PUBLISHER ->
                viewModel.query.value = viewModel.query.value?.copy(publisher = histo.text)
        }
        dismiss()
    }

    private var bottomSheet: FrameLayout? = null

    private fun sizeDialog(height: Int = Int.MAX_VALUE) {
        bottomSheet?.let {
            val layoutParams = it.layoutParams
            layoutParams.height = min(Resources.getSystem().displayMetrics.heightPixels, height)
            it.layoutParams = layoutParams
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        //  if you wanna show the bottom sheet as full screen,
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { dialog: DialogInterface ->
            (dialog as BottomSheetDialog).findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                bottomSheet = it
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return bottomSheetDialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        bottomSheet = null
    }

}