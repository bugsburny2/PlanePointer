package com.example.pointtoplane

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.pointtoplane.ui.main.MainScreen
import com.example.pointtoplane.ui.main.MainViewModel

class MainActivity : ComponentActivity() {

    // Obtain the ViewModel early so we can call retryLocationUpdates()
    // after the permission callback fires
    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            // Now that permission is granted, tell the ViewModel to start GPS
            viewModel.retryLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Trigger the ViewModel to be created now (before setContent)
        // so retryLocationUpdates() can be called in the permission callback
        val vm = viewModel

        requestLocationPermissionsIfNeeded()

        setContent {
            // Pass the already-created ViewModel instance so Compose doesn't
            // create a second one via viewModel()
            MainScreen(viewModel = vm)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onPause()
    }

    private fun requestLocationPermissionsIfNeeded() {
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
