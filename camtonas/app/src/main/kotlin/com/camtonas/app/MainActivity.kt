package com.camtonas.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.camtonas.app.ui.screens.HomeScreen
import com.camtonas.app.ui.screens.LogsScreen
import com.camtonas.app.ui.screens.SettingsScreen
import com.camtonas.app.ui.theme.CamToNasTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm: SyncViewModel by androidx.lifecycle.viewmodels.compose.viewModels()
        setContent {
            CamToNasTheme {
                val permissions = rememberMultiplePermissionsState(
                    listOf(
                        android.Manifest.permission.POST_NOTIFICATIONS,
                        android.Manifest.permission.NEARBY_WIFI_DEVICES,
                    )
                )
                LaunchedEffect(Unit) { permissions.launchMultiplePermissionRequest() }
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(vm = vm)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun AppRoot(vm: SyncViewModel) {
    val nav = rememberNavController()
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.bindService()
        onDispose { vm.unbindService() }
    }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenSettings = { nav.navigate("settings") },
                onOpenLogs = { nav.navigate("logs") },
                vm = vm
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, vm = vm)
        }
        composable("logs") {
            LogsScreen(onBack = { nav.popBackStack() }, vm = vm)
        }
    }
}
