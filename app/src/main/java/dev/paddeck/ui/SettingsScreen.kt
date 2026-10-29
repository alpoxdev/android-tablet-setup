package dev.paddeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.paddeck.data.Prefs

@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    var sensitivity by remember { mutableFloatStateOf(prefs.sensitivity) }
    var naturalScroll by remember { mutableStateOf(prefs.naturalScroll) }
    var tapToClick by remember { mutableStateOf(prefs.tapToClick) }
    var directTouch by remember { mutableStateOf(prefs.directTouch) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("조작 설정", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("닫기") }
        }

        Column {
            Text("포인터 속도", style = MaterialTheme.typography.titleMedium)
            Text(
                "손가락을 움직인 거리에 비해 커서가 얼마나 움직일지 정합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it; prefs.sensitivity = it },
                valueRange = 0.5f..3.5f,
            )
            Text("현재 " + String.format("%.1f", sensitivity) + "배")
        }

        ToggleRow(
            title = "자연스러운 스크롤",
            description = "두 손가락을 위로 밀면 화면 내용이 함께 올라갑니다.",
            checked = naturalScroll,
            onChange = { naturalScroll = it; prefs.naturalScroll = it },
        )
        ToggleRow(
            title = "탭해서 클릭",
            description = "손가락으로 톡 치면 왼쪽 클릭이 됩니다.",
            checked = tapToClick,
            onChange = { tapToClick = it; prefs.tapToClick = it },
        )
        ToggleRow(
            title = "직접 터치",
            description = "손가락이 닿은 곳으로 커서가 바로 이동합니다. 그림 그리기에 편합니다.",
            checked = directTouch,
            onChange = { directTouch = it; prefs.directTouch = it },
        )

        Text(
            "두 손가락으로 문지르면 스크롤, 두 손가락으로 톡 치면 오른쪽 클릭, 세 손가락 위아래로 밀면 미션 컨트롤이 열립니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
