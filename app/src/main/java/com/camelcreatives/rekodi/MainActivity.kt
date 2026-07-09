package com.camelcreatives.rekodi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.camelcreatives.rekodi.ui.theme.RekodiTheme
import com.camelcreatives.rekodi.ui.navigation.RekodiNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            viewModel.startDestination.value == null
        }

        enableEdgeToEdge()
        setContent {
            RekodiTheme {
                val startDestination by viewModel.startDestination.collectAsState()
                startDestination?.let {
                    RekodiNavHost(
                        startDestination = it,
                        onOnboardingComplete = viewModel::completeOnboarding
                    )
                }
            }
        }
    }
}
