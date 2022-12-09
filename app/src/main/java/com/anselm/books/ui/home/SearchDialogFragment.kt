package com.anselm.books.ui.home

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.anselm.books.BooksApplication
import com.anselm.books.Histo
import com.anselm.books.R
import com.anselm.books.databinding.SearchDialogFragmentBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class HistoAdapter(
    private val context: Context,
    private val dataSource: List<Histo>
): BaseAdapter() {
    private val inflater by lazy {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }
    override fun getCount(): Int {
        return dataSource.size
    }

    override fun getItem(position: Int): Any {
        return dataSource[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        var view: View? = convertView
        if (view == null) {
            view = inflater.inflate(R.layout.search_dialog_item_layout, parent, false)
        }
        val histo = dataSource[position]
        view?.findViewById<TextView>(R.id.idHistoValueView)?.text = histo.text
        view?.findViewById<TextView>(R.id.idHistoCountView)?.text = histo.count.toString()
        return view
    }

}

class SearchDialogFragment: BottomSheetDialogFragment() {
    val viewModel: QueryViewModel by activityViewModels()

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

        var values: List<Histo>? = null
        requireActivity().lifecycleScope.launch {
            values = BooksApplication.app.database.bookDao().getPhysicalLocation()
            val adapter = HistoAdapter(activity?.applicationContext!!, values!!)
            binding.idHistoList.adapter = adapter
        }


        binding.idHistoList.setOnItemClickListener { _, _, position, _ ->
            viewModel.location.value = values?.get(position)?.text
            dismiss()
        }
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (true /*isExpanded*/) {
            //  if you wanna show the bottom sheet as full screen,
            val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
            bottomSheetDialog.setOnShowListener { dialog: DialogInterface ->
                val bottomSheet = (dialog as BottomSheetDialog)
                    .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet != null) BottomSheetBehavior
                    .from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
            }
            return bottomSheetDialog
        }
        return super.onCreateDialog(savedInstanceState)
    }

}