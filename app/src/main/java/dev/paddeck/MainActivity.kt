package dev.paddeck

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.paddeck.data.Prefs
import dev.paddeck.input.Keysym
import dev.paddeck.ui.ConnectScreen
import dev.paddeck.ui.RemoteScreen
import dev.paddeck.ui.SettingsScreen
import dev.paddeck.ui.PadDeckTheme
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PadDeckTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PadDeckApp()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val client = SessionBus.client
        if (client != null && SessionBus.remoteInputActive) {
            val keysym = Keysym.fromKeyEvent(event)
            if (keysym != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) client.sendKey(true, keysym)
                } else {
                    client.sendKey(false, keysym)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

private enum class Screen { Connect, Remote, Settings }

@Composable
private fun PadDeckApp() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var screen by remember { mutableStateOf(Screen.Connect) }
    var activeHost by remember { mutableStateOf("") }

    when (screen) {
        Screen.Connect -> ConnectScreen(
            prefs = prefs,
            onConnect = { host, port, password ->
                val current = prefs.profile
                prefs.profile = current.copy(host = host, port = port, password = password)
                activeHost = host
                screen = Screen.Remote
            },
            onOpenSettings = { screen = Screen.Settings },
        )

        Screen.Remote -> RemoteScreen(
            prefs = prefs,
            host = activeHost,
            onDisconnect = { screen = Screen.Connect },
            onOpenSettings = { screen = Screen.Settings },
        )

        Screen.Settings -> SettingsScreen(
            prefs = prefs,
            onBack = { screen = if (SessionBus.client != null) Screen.Remote else Screen.Connect },
        )
    }
}
