package com.anselm.books.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R

data class MenuItemHandler(
    val menuId: Int,
    val handler: (() -> Unit)? = null,
    val prepare: ((MenuItem) -> Unit)? = null
)

open class BookFragment: Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()) {
            if ( ! it ) {
                app.toast(R.string.request_camera_permission)
            }
        }
    }

    private var menuProvider: MenuProvider? = null
    private var listOfHandlers: List<MenuItemHandler> = emptyList()
    /*
     * Displays and installs the provided menu items, returns the current settings sp they can be
     * restored if needed.
     */
    protected fun handleMenu(items: List<MenuItemHandler>): List<MenuItemHandler> {
        val activity = requireActivity()
        val returnValue = listOfHandlers
        if (menuProvider != null) {
            activity.removeMenuProvider(menuProvider!!)
            menuProvider = null
        }
        listOfHandlers = items
        menuProvider = object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Everything is invisible ...
                menu.forEach { it.isVisible = false }
                // Unless requested by the fragment.
                items.forEach { handler ->
                    menu.findItem(handler.menuId)?.let {
                        handler.prepare?.invoke(it)
                        it.isVisible = true
                    }
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val found = items.firstOrNull { menuItem.itemId == it.menuId }
                return if (found != null) {
                    found.handler?.invoke()
                    true
                } else {
                    false
                }
            }
        }
        activity.addMenuProvider(menuProvider!!, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return returnValue
    }

    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    fun checkCameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        menuProvider = null
    }
}