package com.anselm.books

import android.content.SharedPreferences
import android.util.Log
import kotlin.reflect.KMutableProperty0

class BooksPreferences(
    private val prefs: SharedPreferences
) {
    var useGoogle = true
    var useBNF = true
    var useiTunes = true
    var useWorldcat = true
    var useAmazon = true
    var useOpenLibrary = true
    var useOnlyExistingGenres = false

    var displayLastModified = true
    var displayBookId = false
    var enableShortcutToEdit = false

    var wskey = ""

    private lateinit var preferenceMap: Map<String, Pair<Boolean, KMutableProperty0<Boolean>>>

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            val prop = preferenceMap.getOrDefault(key, null)
            if (prop != null) {
                val value = (prefs?.getBoolean(key, false) == true)
                prop.second.setter.invoke(value)
                Log.d(TAG, "$key changed to $value")
            } else if (key == "oclc_wskey") {
                wskey = prefs?.getString(key, "")!!
            }
        }

    init {
        BooksApplication.app.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        preferenceMap = mapOf(
            "lookup_use_only_existing_genres" to Pair(false, ::useOnlyExistingGenres),
            "display_last_modified" to Pair(false, ::displayLastModified),
            "display_book_id" to Pair(false, ::displayBookId),
            "enable_shortcut_to_edit" to Pair(false, ::enableShortcutToEdit),
            "use_google" to Pair(true, ::useGoogle),
            "use_itunes" to Pair(true, ::useiTunes),
            "use_worldcat" to Pair(true, ::useWorldcat),
            "use_amazon" to Pair(true, ::useAmazon),
            "use_open_library" to Pair(false, ::useOpenLibrary),
            "use_bnf" to Pair(true, ::useBNF),
        )
        preferenceMap.forEach { (key: String, prop: Pair<Boolean, KMutableProperty0<Boolean>>) ->
            prop.second.setter(prefs.getBoolean(key, prop.first))
        }
        wskey = prefs.getString("oclc_wskey", "")!!
    }



}
