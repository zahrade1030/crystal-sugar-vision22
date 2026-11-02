package com.crystalsugar.admin.ui

import androidx.compose.runtime.*
import com.crystalsugar.admin.ui.screens.*

@Composable
fun AdminRootApp() {
    var currentScreen by remember { mutableStateOf("admin_splash") }

    when (currentScreen) {
        "admin_splash" -> AdminSplashScreen {
            currentScreen = "admin_login"
        }
        "admin_login" -> AdminLoginScreen {
            currentScreen = "admin_dashboard"
        }
        "admin_dashboard" -> AdminDashboardScreen { route ->
            currentScreen = route
        }
        "admin_customers" -> AdminCustomersScreen {
            currentScreen = "admin_dashboard"
        }
        "admin_licenses" -> AdminLicensesScreen {
            currentScreen = "admin_dashboard"
        }
        "admin_subscriptions" -> AdminSubscriptionsScreen {
            currentScreen = "admin_dashboard"
        }
        "admin_devices" -> AdminDevicesScreen {
            currentScreen = "admin_dashboard"
        }
        "admin_reports" -> AdminReportsScreen {
            currentScreen = "admin_dashboard"
        }
        "admin_settings" -> AdminSettingsScreen {
            currentScreen = "admin_dashboard"
        }
    }
}
