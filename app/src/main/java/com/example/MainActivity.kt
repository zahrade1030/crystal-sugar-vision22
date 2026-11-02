package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.screens.about.AboutScreen
import com.example.ui.screens.calibration.CameraCalibrationScreen
import com.example.ui.screens.developer.DeveloperCenterScreen
import com.example.ui.screens.help.HelpCenterScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.reference.ReferenceDatabaseScreen
import com.example.ui.screens.reports.ReportsScreen
import com.example.ui.screens.result.AdvancedAnalysisCenterScreen
import com.example.ui.screens.result.AnalysisResultScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        setContent {
            val language by mainViewModel.language.collectAsState()
            val themeStr by mainViewModel.theme.collectAsState()
            val isProtected by mainViewModel.isScreenshotProtected.collectAsState()

            // Handle FLAG_SECURE screenshot protection
            if (isProtected) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            val layoutDirection = if (language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            val isDark = themeStr == "DARK"

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MyApplicationTheme(darkTheme = isDark) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(mainViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToResult = { navController.navigate("result") },
                onNavigateToReferences = { navController.navigate("references") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToReports = { navController.navigate("reports") },
                onNavigateToCalibration = { navController.navigate("calibration") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHelp = { navController.navigate("help") },
                onNavigateToDevCenter = { navController.navigate("developer") },
                onNavigateToAbout = { navController.navigate("about") },
                onNavigateToCompare = { navController.navigate("compare") }
            )
        }

        composable("compare") {
            com.example.ui.screens.compare.TwoSampleComparisonScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("result") {
            AnalysisResultScreen(
                viewModel = viewModel,
                onNavigateToAdvanced = { navController.navigate("advanced") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("advanced") {
            AdvancedAnalysisCenterScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("references") {
            ReferenceDatabaseScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("reports") {
            ReportsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("calibration") {
            CameraCalibrationScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("developer") {
            DeveloperCenterScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("help") {
            HelpCenterScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("about") {
            AboutScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
