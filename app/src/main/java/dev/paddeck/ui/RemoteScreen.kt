package dev.paddeck.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.paddeck.SessionBus
import dev.paddeck.data.Prefs
import dev.paddeck.input.Keysym
import dev.paddeck.input.RemoteInput
import dev.paddeck.input.TrackpadEngine
import dev.paddeck.vnc.RfbClient

private const val FRAME_INTERVAL_MS = 33L

@Composable
fun RemoteScreen(
    prefs: Prefs,
    host: String,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val profile = remember { prefs.profile }

    var status by remember { mutableStateOf("연결하는 중") }
    var connected by remember { mutableStateOf(false) }
    var desktopName by remember { mutableStateOf(host) }
    var frame by remember { mutableIntStateOf(0) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var directTouch by remember { mutableStateOf(prefs.directTouch) }

    val bitmapRef = remember { arrayOfNulls<Bitmap>(1) }
    val pixelsRef = remember { arrayOfNulls<IntArray>(1) }
    val lastDraw = remember { longArrayOf(0) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val focusRequester = remember { FocusRequester() }

    val engine = remember {
        val sink: (RemoteInput) -> Unit = { input ->
            val client = SessionBus.client
            if (client != null) {
                when (input) {
                    is RemoteInput.Pointer -> client.sendPointer(input.buttonMask, input.x, input.y)
                    is RemoteInput.Key -> client.sendKey(input.down, input.keysym)
                }
            }
        }
        TrackpadEngine(sink).apply {
            sensitivity = prefs.sensitivity
            naturalScroll = prefs.naturalScroll
            tapToClick = prefs.tapToClick
        }
    }

    val pointerHandler = remember { CapturedPointerHandler(engine) }

    DisposableEffect(host) {
        val holder = arrayOfNulls<RfbClient>(1)
        val client = RfbClient(
            host = host,
            port = profile.port,
            password = profile.password,
            listener = object : RfbClient.Listener {
                override fun onReady(name: String, width: Int, height: Int) {
                    handler.post {
                        desktopName = if (name.isBlank()) host else name
                        status = "$width × $height"
                        connected = true
                        SessionBus.remoteInputActive = true
                        engine.remoteWidth = width
                        engine.remoteHeight = height
                        bitmapRef[0] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        pixelsRef[0] = IntArray(width * height)
                        imageBitmap = bitmapRef[0]
                        frame++
                    }
                }

                override fun onResize(width: Int, height: Int) {
                    handler.post {
                        engine.remoteWidth = width
                        engine.remoteHeight = height
                        bitmapRef[0] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        pixelsRef[0] = IntArray(width * height)
                        imageBitmap = bitmapRef[0]
                        frame++
                    }
                }

                override fun onFrame(dirtyX: Int, dirtyY: Int, dirtyWidth: Int, dirtyHeight: Int) {
                    val now = System.currentTimeMillis()
                    if (now - lastDraw[0] < FRAME_INTERVAL_MS) return
                    lastDraw[0] = now
                    handler.post {
                        val target = bitmapRef[0] ?: return@post
                        val pixels = pixelsRef[0] ?: return@post
                        if (holder[0]?.copyPixelsInto(pixels) == true) {
                            target.setPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
                            frame++
                        }
                    }
                }

                override fun onClipboard(text: String) = Unit

                override fun onBell() {
                    handler.post { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                }

                override fun onClosed(reason: String) {
                    handler.post {
                        connected = false
                        status = reason
                        SessionBus.remoteInputActive = false
                    }
                }
            },
        )

        SessionBus.client = client
        holder[0] = client
        client.start()

        view.setOnCapturedPointerListener { _, event ->
            pointerHandler.onEvent(event)
            true
        }

        onDispose {
            SessionBus.client = null
            SessionBus.remoteInputActive = false
            view.setOnCapturedPointerListener(null)
            client.close()
        }
    }

    LaunchedEffect(directTouch) {
        engine.sensitivity = prefs.sensitivity
        engine.naturalScroll = prefs.naturalScroll
        engine.tapToClick = prefs.tapToClick
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0C0F))) {
        TopBar(
            title = desktopName,
            status = status,
            connected = connected,
            capturing = capturing,
            onToggleCapture = {
                if (capturing) {
                    view.releasePointerCapture()
                    capturing = false
                } else {
                    view.isFocusableInTouchMode = true
                    view.requestFocus()
                    view.requestPointerCapture()
                    capturing = true
                }
            },
            onToggleDirectTouch = {
                directTouch = !directTouch
                prefs.directTouch = directTouch
            },
            onKeyboard = {
                focusRequester.requestFocus()
                val imm = context.getSystemService(InputMethodManager::class.java)
                imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            },
            onSettings = onOpenSettings,
            onBack = onDisconnect,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .trackpadGestures(engine, directTouch),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = imageBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "원격 화면",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!connected) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(status, color = Color(0xFFB9C8D6), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDisconnect) { Text("돌아가기") }
                }
            }
        }

        RemoteKeyBar(engine = engine, client = { SessionBus.client })

        BasicTextField(
            value = "",
            onValueChange = {},
            textStyle = TextStyle(fontSize = 1.sp, color = Color.Transparent),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
                .focusRequester(focusRequester),
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    status: String,
    connected: Boolean,
    capturing: Boolean,
    onToggleCapture: () -> Unit,
    onToggleDirectTouch: () -> Unit,
    onKeyboard: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = Color(0xFF14171C)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onBack) { Text("닫기") }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFFE7EBEF), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (connected) status else "연결 끊김 · $status",
                    color = Color(0xFF8C99A6),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = onToggleCapture) { Text(if (capturing) "포인터 해제" else "마우스 잡기") }
            TextButton(onClick = onToggleDirectTouch) { Text("직접 터치") }
            TextButton(onClick = onKeyboard) { Text("키보드") }
            TextButton(onClick = onSettings) { Text("설정") }
        }
    }
}

@Composable
private fun RemoteKeyBar(engine: TrackpadEngine, client: () -> RfbClient?) {
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var cmd by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }

    fun toggleModifier(keysym: Int, current: Boolean, set: (Boolean) -> Unit) {
        val next = !current
        client()?.sendKey(next, keysym)
        set(next)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14171C))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyChip("⌃", ctrl, Modifier.weight(1f)) { toggleModifier(Keysym.CONTROL_L, ctrl) { ctrl = it } }
        KeyChip("⌥", alt, Modifier.weight(1f)) { toggleModifier(Keysym.ALT_L, alt) { alt = it } }
        KeyChip("⌘", cmd, Modifier.weight(1f)) { toggleModifier(Keysym.SUPER_L, cmd) { cmd = it } }
        KeyChip("⇧", shift, Modifier.weight(1f)) { toggleModifier(Keysym.SHIFT_L, shift) { shift = it } }
        KeyChip("esc", false, Modifier.weight(1f)) { pressOnce(client(), Keysym.ESCAPE) }
        KeyChip("⇥", false, Modifier.weight(1f)) { pressOnce(client(), Keysym.TAB) }
        KeyChip("←", false, Modifier.weight(1f)) { pressOnce(client(), Keysym.LEFT) }
        KeyChip("↑", false, Modifier.weight(1f)) { pressOnce(client(), Keysym.UP) }
        KeyChip("↓", false, Modifier.weight(1f)) { pressOnce(client(), Keysym.DOWN) }
        KeyChip("→", false, Modifier.weight(1f)) { pressOnce(client(), Keysym.RIGHT) }
        KeyChip("⌫", false, Modifier.weight(1f)) { pressOnce(client(), Keysym.BACKSPACE) }
        KeyChip("⏎", false, Modifier.weight(1f)) { pressOnce(client(), Keysym.RETURN) }
        KeyChip("우클릭", false, Modifier.weight(1.4f)) { engine.tapRight() }
    }
}

@Composable
private fun KeyChip(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (active) Color(0xFF7FD1FF) else Color(0xFF232A33),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                color = if (active) Color(0xFF00212E) else Color(0xFFE7EBEF),
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

private fun pressOnce(client: RfbClient?, keysym: Int) {
    client?.sendKey(true, keysym)
    client?.sendKey(false, keysym)
}
