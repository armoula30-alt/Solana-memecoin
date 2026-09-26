package com.solanasignal.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.solanasignal.app.ui.AppViewModel
import com.solanasignal.app.ui.dashboard.DashboardScreen
import com.solanasignal.app.ui.detail.TokenDetailScreen
import com.solanasignal.app.ui.history.SignalHistoryScreen
import com.solanasignal.app.ui.scanner.LiveScannerScreen
import com.solanasignal.app.ui.settings.SettingsScreen
import com.solanasignal.app.ui.status.SystemStatusScreen
import com.solanasignal.app.ui.theme.SolanaSignalTheme

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Home)
    object Scanner : Screen("scanner", "Scanner", Icons.Filled.List)
    object History : Screen("history", "History", Icons.Filled.DateRange)
    object Status : Screen("status", "Status", Icons.Filled.Info)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Holds a mint the app should jump straight to (from a tapped signal notification),
    // as Compose state so both cold start (onCreate) and warm start (onNewIntent,
    // since MainActivity is singleTop) can drive navigation the same way.
    private val pendingNavigateMint = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        pendingNavigateMint.value = intent?.getStringExtra("navigate_to_mint")

        setContent {
            SolanaSignalTheme {
                val vm: AppViewModel = viewModel()
                AppScaffold(vm, pendingNavigateMint)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The app was already open (singleTop) - a fresh signal notification was tapped.
        intent.getStringExtra("navigate_to_mint")?.let { pendingNavigateMint.value = it }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun AppScaffold(vm: AppViewModel, pendingNavigateMint: MutableState<String?> = mutableStateOf(null)) {
    val navController = rememberNavController()
    val items = listOf(Screen.Dashboard, Screen.Scanner, Screen.History, Screen.Status, Screen.Settings)

    // Fires once per tapped notification: navigate straight to that token's detail
    // screen, then clear the pending value so rotating the screen etc. doesn't repeat it.
    val pendingMint = pendingNavigateMint.value
    LaunchedEffect(pendingMint) {
        if (pendingMint != null) {
            navController.navigate("detail/$pendingMint") { launchSingleTop = true }
            pendingNavigateMint.value = null
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(vm) }
            composable(Screen.Scanner.route) { LiveScannerScreen(vm) { mint -> navController.navigate("detail/$mint") } }
            composable(Screen.History.route) { SignalHistoryScreen(vm) }
            composable(Screen.Status.route) { SystemStatusScreen(vm) }
            composable(Screen.Settings.route) { SettingsScreen(vm) }
            composable("detail/{mint}") { backStackEntry ->
                val mint = backStackEntry.arguments?.getString("mint") ?: ""
                TokenDetailScreen(vm, mint)
            }
        }
    }
}
