package com.feilema.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.feilema.app.core.navigation.FeiLeMaNavHost
import com.feilema.app.core.ui.components.AppBackground
import com.feilema.app.core.ui.theme.FeiLeMaTheme
import com.feilema.app.core.ui.theme.ThemeMode
import com.feilema.app.data.preferences.ThemePreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by themePreferences.settings.collectAsState()
            val darkTheme = when (settings.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FeiLeMaTheme(darkTheme = darkTheme, seedColor = settings.themeSeed) {
                AppBackground(backgroundUri = settings.backgroundUri) {
                    FeiLeMaNavHost()
                }
            }
        }
    }
}
