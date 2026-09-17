package com.mohdshayan.kickset

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohdshayan.kickset.di.ServiceLocator
import com.mohdshayan.kickset.ui.nav.AppNav
import com.mohdshayan.kickset.ui.settings.UnitsSheet
import com.mohdshayan.kickset.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = ServiceLocator.appPrefs
        setContent {
            val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { prefs.markOpened(System.currentTimeMillis()) }
            AppTheme(themeMode = settings?.themeMode ?: "SYSTEM") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val s = settings
                    if (s != null) {
                        AppNav()
                        if (!s.unitsChosen) UnitsSheet { unit -> scope.launch { prefs.setUnitSystem(unit) } }
                    }
                }
            }
        }
    }
}
