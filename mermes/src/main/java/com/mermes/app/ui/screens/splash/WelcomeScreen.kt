package com.mermes.app.ui.screens.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onConnected: () -> Unit
) {
    var selectedMode by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "欢迎使用 Mermes",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "选择连接模式开始使用",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 本地 Termux 模式
        ConnectionModeCard(
            title = "本地 Termux 模式",
            description = "一键启动本地环境，无需网络连接",
            isSelected = selectedMode == "local",
            onClick = { selectedMode = "local" }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 远程 HTTP 模式
        ConnectionModeCard(
            title = "远程 HTTP 模式",
            description = "连接到远程服务器，输入 Server URL",
            isSelected = selectedMode == "http",
            onClick = { selectedMode = "http" }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 远程 SSH 隧道模式
        ConnectionModeCard(
            title = "远程 SSH 隧道模式",
            description = "通过 SSH 隧道安全连接到远程服务器",
            isSelected = selectedMode == "ssh",
            onClick = { selectedMode = "ssh" }
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onConnected() },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedMode != null
        ) {
            Text("连接")
        }
    }
}

@Composable
private fun ConnectionModeCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
        }
    }
}
