package com.snoozetravel.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.data.ThemeMode
import com.snoozetravel.app.ui.theme.SnoozeTheme
import com.snoozetravel.app.ui.theme.isDark

enum class Screen { HOME, EDITOR, SETTINGS }

class NavViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.HOME)
}

class MainActivity : ComponentActivity() {
    private val nav: NavViewModel by viewModels()
    private val editor: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val themeMode by Store.theme.collectAsStateWithLifecycle()
            val dark = isDark(themeMode)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            SnoozeTheme(dark) {
                AppRoot(nav, editor, dark, onToggleTheme = { Store.setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Lugar compartido desde Google Maps (texto con enlace) o enlace geo:. */
    private fun handleIntent(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> i.dataString
            else -> null
        } ?: return
        editor.load(null)
        editor.handleShared(text)
        nav.screen = Screen.EDITOR
    }
}

@Composable
private fun AppRoot(nav: NavViewModel, editor: EditorViewModel, dark: Boolean, onToggleTheme: () -> Unit) {
    val activate = rememberTripActivator()
    BackHandler(nav.screen != Screen.HOME) { nav.screen = Screen.HOME }

    AnimatedContent(
        targetState = nav.screen,
        transitionSpec = {
            val forward = targetState != Screen.HOME
            (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { if (forward) it / 5 else -it / 5 }) togetherWith
                (fadeOut(tween(200)) + slideOutHorizontally(tween(280)) { if (forward) -it / 5 else it / 5 })
        },
        label = "nav",
    ) { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                dark = dark,
                onToggleTheme = onToggleTheme,
                onOpenSettings = { nav.screen = Screen.SETTINGS },
                onNewDestination = { editor.load(null); nav.screen = Screen.EDITOR },
                onEditDestination = { editor.load(it); nav.screen = Screen.EDITOR },
                onActivate = activate,
            )
            Screen.EDITOR -> EditorScreen(editor, dark, onDone = { nav.screen = Screen.HOME })
            Screen.SETTINGS -> SettingsScreen(onBack = { nav.screen = Screen.HOME })
        }
    }
}
