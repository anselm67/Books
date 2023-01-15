package com.anselm.books

import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.database.BookRepository
import com.anselm.books.database.BookRepositoryListener
import com.anselm.books.database.Label

class LastLocationPreference(
    private val repository: BookRepository,
    private var lastLocation: Label? = null,
    private var isEnabled: Boolean = false,
) {
    init {
        isEnabled = app.prefs.getBoolean("lookup_use_last_location", true)

        // Listens to enable/disable signal from a property change.
        app.prefs.registerOnSharedPreferenceChangeListener { prefs, key ->
            if (key != "lookup_use_last_location") {
                return@registerOnSharedPreferenceChangeListener
            }
            val newValue = prefs.getBoolean("lookup_use_last_location", true)
            if (newValue != isEnabled) {
                lastLocation = null
            }
            isEnabled = newValue
        }

        // Hook into the repository to update our last known location.
        repository.addBookListener(object : BookRepositoryListener {
            override fun onBookDeleted(book: Book) { }

            override fun onBookInserted(book: Book) {
                handleInsert(book)
            }

            override fun onBookUpdated(book: Book) {
                handleInsert(book)
            }

            override fun onBookCreated(book: Book) {
                if (isEnabled && lastLocation != null) {
                    book.location = getLastLocation()
                }
            }
        })
    }

    private fun handleInsert(book: Book) {
        if (!isEnabled || (book.location == lastLocation) || (book.location == null)) {
            return
        }
        lastLocation = book.location
        val editor = app.prefs.edit()
        editor.putString("lookup_use_last_location_value", lastLocation!!.name)
        editor.apply()
    }

    fun getLastLocation(): Label? {
        if (lastLocation == null) {
            val locationName = app.prefs.getString("lookup_use_last_location_value", "")!!
            if ( locationName.isNotEmpty()) {
                lastLocation = repository.labelB(Label.Type.Location, locationName)
            }
        }
        return lastLocation
    }

}