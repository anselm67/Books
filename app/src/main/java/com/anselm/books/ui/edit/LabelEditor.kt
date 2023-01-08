package com.anselm.books.ui.edit

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.BookDao
import com.anselm.books.database.Label
import com.anselm.books.databinding.AutocompleteLabelLayoutBinding
import com.anselm.books.databinding.EditMultiLabelLayoutBinding
import com.anselm.books.databinding.EditSingleLabelLayoutBinding
import com.anselm.books.ui.widgets.DnDList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


private class LabelArrayAdapter(
    val context: Activity,
    val type: Label.Type,
    val labels: List<Label>,
): ArrayAdapter<Label>(context, 0, labels) {
    lateinit var binding: AutocompleteLabelLayoutBinding

    override fun getView(position: Int, converterView: View?, parent: ViewGroup): View {
        var view = converterView
        if (view == null) {
            binding = AutocompleteLabelLayoutBinding.inflate(
                LayoutInflater.from(context), parent, false
            )
            view = binding.root
        }
        val label = getItem(position)
        if (label != null) {
            view.findViewById<TextView>(R.id.autoCompleteText).text = label.name
        }
        return view
    }

    inner class LabelFilter: Filter() {

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val repository = BooksApplication.app.repository
            val labelQuery = if (constraint?.isNotEmpty() == true) "$constraint*" else ""
            val results = FilterResults()
            runBlocking {
                val histos = BooksApplication.app.repository.getHisto(
                    type,
                    labelQuery,
                    sortBy = BookDao.SortByTitle,
                )
                results.count = histos.size
                results.values = histos.map { repository.label(it.labelId) }
                Log.d(TAG, "Returning ${histos.size} results.")
            }
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            clear()
            if (results != null ) {
                @Suppress("UNCHECKED_CAST")
                addAll(results.values as List<Label>)
            }
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return (resultValue as Label).name
        }
    }

    override fun getFilter(): Filter {
        return LabelFilter()
    }
}

private class LabelAutoComplete(
    val fragment: EditFragment,
    val autoComplete: AutoCompleteTextView,
    val type: Label.Type,
    val initialValue: Label? = null,
    val handleLabel: (Label) -> Unit,
    val onChange: ((String) -> Unit)? = null
) {
    private val repository = BooksApplication.app.repository
    private val initialText = initialValue?.name ?: ""

    init {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val labels = repository.getHisto(type, sortBy = BookDao.SortByTitle).map {
                repository.label(it.labelId)
            }
            val adapter = LabelArrayAdapter(
                fragment.requireActivity(),
                type,
                labels,
            )
            autoComplete.threshold = 1
            if (initialValue != null) {
                autoComplete.setText(initialValue.name, false)
            }
            autoComplete.setAdapter(adapter)
            autoComplete.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val input = s.toString()
                    if (input != initialText) {
                        onChange?.invoke(input)
                    }
                }
            })
            autoComplete.setOnItemClickListener { parent, _, position, _ ->
                handleLabel(parent.getItemAtPosition(position) as Label)
            }
            autoComplete.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    fragment.scrollTo(autoComplete.parent as View)
                }
            }
        }
        autoComplete.setOnEditorActionListener(object: TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId != EditorInfo.IME_ACTION_DONE)
                    return false
                val text = if (v == null || v.text == null) "" else v.text.toString().trim()
                if (text != initialText) {
                    handleLabel(repository.labelB(type, text))
                }
                return false
            }

        })

    }
}

class MultiLabelEditor(
    fragment: EditFragment,
    inflater: LayoutInflater,
    val type: Label.Type,
    val labelId: Int,
    val getter: () -> List<Label>,
    val setter: (List<Label>) -> Unit
) : Editor (fragment, inflater) {
    private var _binding: EditMultiLabelLayoutBinding? = null
    private val editor get() = _binding!!
    private lateinit var dndlist: DnDList

    override fun setup(container: ViewGroup?): View {
        _binding = EditMultiLabelLayoutBinding.inflate(inflater, container, false)
        editor.idEditLabel.text = fragment.getText(labelId)
        editor.labels.layoutManager = LinearLayoutManager(editor.labels.context)

        // Sets up te drag and drop list view for displaying the existing labels.
        dndlist = DnDList(
            editor.labels,
            getter().toMutableList(),
            onChange = { newLabels ->
                if (newLabels != getter()) {
                    fragment.setChanged(editor.root, editor.idUndoEdit)
                } else {
                    fragment.setUnchanged(editor.root, editor.idUndoEdit)
                }
            }
        )

        // Sets up the auto-complete for entering new labels.
        LabelAutoComplete(fragment, editor.autoComplete, type,
            handleLabel = { addLabel(it) }
        )
        // Sets up the undo button.
        editor.idUndoEdit.setOnClickListener {
            fragment.setUnchanged(editor.root, editor.idUndoEdit)
            dndlist.setLabels(getter().toMutableList())
        }
        return editor.root
    }

    override fun isChanged(): Boolean {
        return dndlist.getLabels() != getter()
    }

    override fun saveChange() {
        setter(dndlist.getLabels())
    }

    fun addLabel(label: Label) {
        if ( dndlist.addLabel(label) ) {
            fragment.setChanged(editor.root, editor.idUndoEdit)
        }
        editor.autoComplete.setText("")
    }
}

class SingleLabelEditor(
    fragment: EditFragment,
    inflater: LayoutInflater,
    val type: Label.Type,
    val labelId: Int,
    val getter: () -> Label?,
    val setter: (Label) -> Unit
): Editor (fragment, inflater) {
    private var _binding: EditSingleLabelLayoutBinding? = null
    private val editor get() = _binding!!
    private var editLabel: Label? = null
    private val origText = if (getter() == null) "" else getter()!!.name

    override fun setup(container: ViewGroup?): View {
        _binding = EditSingleLabelLayoutBinding.inflate(inflater, container, false)
        editor.idEditLabel.text = fragment.getText(labelId)

        // Sets up the auto-complete for entering new labels.
        LabelAutoComplete(fragment, editor.autoComplete, type, getter(),
            handleLabel = { setLabel(it) },
            onChange = {
                fragment.setChanged(editor.root, editor.idUndoEdit)
            }
        )
        // Sets up the undo button.
        editor.idUndoEdit.setOnClickListener {
            fragment.setUnchanged(editor.root, editor.idUndoEdit)
            editor.autoComplete.setText(origText)
        }
        return editor.root
    }

    override fun isChanged(): Boolean {
        return (editLabel != null) && (getter() != editLabel)
    }

    override fun saveChange() {
        check(editLabel != null)
        editLabel?.let { setter(it) }
    }

    fun setLabel(label: Label) {
        if (label != getter()) {
            editLabel = label
            fragment.setChanged(editor.root, editor.idUndoEdit)
        }
    }
}
