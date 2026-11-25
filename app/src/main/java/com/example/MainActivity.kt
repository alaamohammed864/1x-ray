package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.OnboardingScreen
import com.example.ui.XRayMirrorScreen
import com.example.ui.XRayViewModel
import com.example.ui.theme.XRayMirrorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: XRayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            XRayMirrorTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) {
                        viewModel.onStartClicked()
                    } else {
                        viewModel.onPermissionDenied()
                    }
                }

                // If permission was granted via Settings while user returned to the app
                LaunchedEffect(Unit) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted && state.cameraPermissionDenied) {
                        viewModel.onStartClicked()
                    }
                }

                if (state.started) {
                    XRayMirrorScreen(viewModel = viewModel)
                } else {
                    OnboardingScreen(
                        permissionDenied = state.cameraPermissionDenied,
                        errorMessage = state.cameraError,
                        onStartCamera = {
                            val granted = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.onStartClicked()
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            )
                            startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}
