package com.javargasp.tiominutermvi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.data.content.AnimationLocator
import com.javargasp.tiominutermvi.domain.auth.AuthPreferences
import com.javargasp.tiominutermvi.ui.components.LocalAnimationLocator
import com.javargasp.tiominutermvi.ui.navigation.AppNavigation
import com.javargasp.tiominutermvi.ui.navigation.Routes
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val animationLocator: AnimationLocator by inject()
    private val authPreferences: AuthPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Read synchronously before the first frame: no Welcome/Home flicker
        val startDestination = if (authPreferences.isStartChoiceMade()) Routes.HOME else Routes.WELCOME
        setContent {
            TioMinuterMVITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompositionLocalProvider(LocalAnimationLocator provides animationLocator) {
                        AppNavigation(startDestination = startDestination)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TioMinuterMVITheme {
        AppNavigation()
    }
}