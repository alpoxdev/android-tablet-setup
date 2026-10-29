package dev.paddeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.paddeck.data.Prefs

@Composable
fun ConnectScreen(
    prefs: Prefs,
    onConnect: (String, Int, String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val saved = remember { prefs.profile }
    var host by remember { mutableStateOf(saved.host) }
    var port by remember { mutableStateOf(saved.port.toString()) }
    var password by remember { mutableStateOf(saved.password) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("PadDeck", style = MaterialTheme.typography.headlineLarge)
        Text(
            "맥북의 화면을 띄우고, 태블릿을 트랙패드와 키보드처럼 씁니다.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it; error = null },
            label = { Text("맥 주소") },
            placeholder = { Text("192.168.0.10 또는 mac.local") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("포트") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("VNC 비밀번호") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
        }

        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (host.isBlank()) {
                        error = "맥 주소를 입력해 주세요"
                    } else {
                        onConnect(host.trim(), port.toIntOrNull() ?: 5900, password)
                    }
                },
                modifier = Modifier.height(52.dp),
            ) {
                Text("연결")
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onOpenSettings) { Text("조작 설정") }
        }

        Spacer(Modifier.height(8.dp))
        MacGuideCard()
    }
}

@Composable
private fun MacGuideCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("맥북에서 먼저 준비할 것", style = MaterialTheme.typography.titleMedium)
            Text(
                "1. 시스템 설정 → 일반 → 공유 → 화면 공유를 켭니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "2. 같은 화면에서 컴퓨터 설정 → 암호를 정하고, \"VNC 사용자가 암호로 제어하도록 허용\"을 켭니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "3. 맥 주소는 시스템 설정 → Wi-Fi → 세부사항에서 확인할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "4. 같은 와이파이에 있을 때만 주소가 그대로 통합니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
