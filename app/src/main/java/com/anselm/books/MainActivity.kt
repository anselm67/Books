package com.anselm.books

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.anselm.books.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val topLevelDestinationIds = setOf(R.id.nav_home)
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(topLevelDestinationIds, drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Sets up the camera permission launcher just in case.
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()) {
            if ( ! it ) {
                BooksApplication.app.toast(R.string.request_camera_permission)
            }
        }

        BooksApplication.app.enableProgressBar(findViewById(R.id.progress_bar))
        BooksApplication.app.enableTitle { title: String ->
            binding.appBarMain.toolbar.title = title
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return if ( topLevelDestinationIds.contains(navController.currentDestination?.id) ){
            navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
        } else {
            onBackPressedDispatcher.onBackPressed()
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BooksApplication.app.disableProgressBar()
    }

    fun checkCameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        return false
    }

}