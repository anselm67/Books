package com.anselm.books.ui.edit

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.NumberPicker.OnValueChangeListener
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.Book
import com.anselm.books.BooksApplication
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentEditBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

class EditFragment: Fragment() {
    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!
    private var book: Book? = null

    private var validBorder: Drawable? = null
    private var invalidBorder: Drawable? = null
    private var changedBorder: Drawable? = null

    private fun getBorderDrawable(resourceId: Int): Drawable {
        return ResourcesCompat.getDrawable(resources, resourceId, null)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val repository = (activity?.application as BooksApplication).repository
        val safeArgs: EditFragmentArgs by navArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            book = repository.getBook(safeArgs.bookId)
            book?.let { bind(it) }
        }

        // Caches the borders corresponding to the various states of individual field editors.
        validBorder = getBorderDrawable(R.drawable.textview_border)
        invalidBorder = getBorderDrawable(R.drawable.textview_border_invalid)
        changedBorder = getBorderDrawable(R.drawable.textview_border_changed)

        handleMenu(requireActivity())
        return root
    }

    private fun setInvalidBorder(view: View) {
        view.background = invalidBorder
    }

    private fun setChangedBorder(view: View) {
        view.background = changedBorder
    }

    private fun setRegularBorder(view: View) {
        view.background = validBorder
    }

    private fun setBorder(editText: EditText, currentValue: String?) {
        val newValue = editText.text.toString()
        if (newValue == currentValue) {
            setRegularBorder(editText)
        } else {
            setChangedBorder(editText)
        }
    }

    private fun bindEditText(
        editText: EditText,
        getter: KProperty0.Getter<String>,
        checker: ((String) -> Boolean)? = null) {
        editText.setText(getter())
        editText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

            override fun afterTextChanged(s: Editable?) {
                val value = s.toString().trim()
                if ( checker != null && ! checker(value) ) {
                    setInvalidBorder(editText)
                } else {
                    setBorder(editText, getter())
                }
            }
        })
    }

    private fun getYearPublishedValue(): Int {
        return (binding.yearPublished100Picker.value * 100
                + binding.yearPublished10Picker.value * 10
                + binding.yearPublished1Picker.value)
    }

    private fun setYearPublishedValue(value: Int) {
        if (value in Book.MIN_PUBLISHED_YEAR..Book.MAX_PUBLISHED_YEAR) {
            binding.yearPublished100Picker.value = value / 100
            binding.yearPublished10Picker.value = (value / 100) % 10
            binding.yearPublished1Picker.value = value % 10
        }
    }

    private fun bindYearPublished() {
        binding.yearPublished100Picker.minValue = Book.MIN_PUBLISHED_YEAR / 100
        binding.yearPublished100Picker.maxValue = Book.MAX_PUBLISHED_YEAR / 100
        binding.yearPublished10Picker.minValue = 0
        binding.yearPublished10Picker.maxValue = 9
        binding.yearPublished1Picker.minValue = 0
        binding.yearPublished1Picker.maxValue = 9
        val yearPublished = book?.yearPublished?.toIntOrNull()
        setYearPublishedValue(if (yearPublished == null ) 0 else yearPublished)
        val onValueChanged = OnValueChangeListener { _, _, _ ->
            val newValue = getYearPublishedValue()
            if (newValue != book?.yearPublished?.toIntOrNull()) {
                setChangedBorder(binding.yearPublishedView)
            } else {
                setRegularBorder(binding.yearPublishedView)
            }
        }
        binding.yearPublished100Picker.setOnValueChangedListener(onValueChanged)
        binding.yearPublished10Picker.setOnValueChangedListener(onValueChanged)
        binding.yearPublished1Picker.setOnValueChangedListener(onValueChanged)
    }

    private fun bind(book: Book) {
        val app = BooksApplication.app
        // Binds all the simple text fields.
        bindEditText(binding.titleView, book::title.getter)
        bindEditText(binding.subtitleView, book::subtitle.getter)
        bindEditText(binding.authorView, book::author.getter)
        bindEditText(binding.summaryView, book::summary.getter)
        bindEditText(binding.isbnView, book::isbn.getter) { value -> isValidEAN13(value) }
        // Binds the year published number picker.
        bindYearPublished()
        // Binds the cover to its image via Glide.
        if (book.imageFilename != "") {
            Glide.with(app.applicationContext)
                .load(app.getCoverUri(book.imageFilename)).centerCrop()
                .placeholder(R.mipmap.ic_book_cover)
                .into(binding.coverImageView)
        } else {
            Glide.with(app.applicationContext)
                .load(R.mipmap.ic_book_cover)
                .into(binding.coverImageView)
        }
    }

    private fun updateValue(editText: EditText, getter: KProperty0.Getter<String>, setter: KMutableProperty0.Setter<String>):Boolean {
        val value = editText.text.toString()
        return if (value != getter()) {
            setter(value)
            true
        } else {
            false
        }
    }

    private fun updateYearPublished(): Boolean {
        val value = getYearPublishedValue()
        if (value != 0 && value != book?.yearPublished?.toIntOrNull()) {
            book?.yearPublished = value.toString()
            return true
        }
        return false
    }

    private fun saveChanges() {
        if (book == null) {
            return
        }
        val changed = (updateValue(binding.titleView, book!!::title.getter, book!!::title.setter)
                || updateValue(binding.subtitleView, book!!::subtitle.getter, book!!::subtitle.setter)
                || updateValue(binding.authorView, book!!::author.getter, book!!::author.setter)
                || updateValue(binding.summaryView, book!!::summary.getter, book!!::summary.setter)
                || updateYearPublished())
        if (changed) {
            activity?.lifecycleScope?.launch {
                val app = BooksApplication.app
                app.database.bookDao().update(book!!)
                app.toast("${book?.title} updated.")
            }
        }
        findNavController().popBackStack()
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.findItem(R.id.idSearchView)?.isVisible = false
                menu.findItem(R.id.idEditBook)?.isVisible = false
                menu.findItem(R.id.idSaveBook)?.isVisible = true
                menu.findItem(R.id.idGotoSearchView)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.idSaveBook) {
                    Log.d(TAG, "Saving book ${this@EditFragment.book?.id}.")
                    saveChanges()
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun digit(c: Char): Int {
        return c.digitToInt()
    }

    private fun isValidEAN13(isbn: String): Boolean {
        if (isbn.length != 13) {
            return false
        }
        // Computes the expected checksum / last digit.
        val sum1 = arrayListOf(0, 2, 4, 6, 8, 10).sumOf { it -> digit(isbn[it]) }
        val sum2 = 3 * arrayListOf(1, 3, 5, 7, 9, 11).sumOf { it -> digit(isbn[it]) }
        val checksum = (sum1 + sum2) % 10
        val expected = if (checksum == 0) '0' else ('0' + 10 - checksum)
        return expected == isbn[12]
    }
}

