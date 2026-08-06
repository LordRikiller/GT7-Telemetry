package com.gt7telemetry

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gt7telemetry.ui.DashboardScreen
import com.gt7telemetry.ui.DashboardViewModel
import com.gt7telemetry.ui.EngineerScreen
import com.gt7telemetry.ui.Gt7Theme
import com.gt7telemetry.ui.LoggerScreen
import com.gt7telemetry.ui.SettingsScreen
import com.gt7telemetry.ui.SetupScreen
import com.gt7telemetry.update.UpdateInstaller
import com.gt7telemetry.update.UpdateState

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* either way, carry on */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the screen awake — this is a mounted instrument.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Android 13+ needs runtime permission to show the ongoing notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start listening for GT7's telemetry stream.
        TelemetryService.start(this)

        setContent {
            Gt7Theme {
                AppRoot(viewModel = viewModel)
            }
        }
    }
}

private enum class Screen { DASHBOARD, SETTINGS, LOGGER, ENGINEER, SETUP }

@Composable
private fun AppRoot(viewModel: DashboardViewModel) {
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }

    // Hand a finished download to the system installer. This lives here — above
    // both screens — so it fires no matter which screen is showing when the
    // download completes (e.g. when the update was started from Settings).
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    LaunchedEffect(updateState) {
        (updateState as? UpdateState.ReadyToInstall)?.let { UpdateInstaller.install(context, it.file) }
    }

    when (screen) {
        Screen.DASHBOARD -> DashboardScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenLogger = { screen = Screen.LOGGER },
            onOpenEngineer = { screen = Screen.ENGINEER },
            onOpenSetup = { screen = Screen.SETUP },
        )
        Screen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.DASHBOARD },
        )
        Screen.LOGGER -> LoggerScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.DASHBOARD },
            onOpenEngineer = { screen = Screen.ENGINEER },
        )
        Screen.ENGINEER -> EngineerScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.DASHBOARD },
            onOpenLogger = { screen = Screen.LOGGER },
        )
        Screen.SETUP -> SetupScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.DASHBOARD },
            onOpenEngineer = { screen = Screen.ENGINEER },
        )
    }
}
