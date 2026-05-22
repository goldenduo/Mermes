package com.mermes.app.ui.screens.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mermes.app.data.model.Provider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onNavigateBack: () -> Unit
) {
    var providers by remember { mutableStateOf(getMockProviders()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "提供商配置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(providers) { provider ->
                ProviderCard(
                    provider = provider,
                    onUpdate = { updated ->
                        providers = providers.map {
                            if (it.id == updated.id) updated else it
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    onUpdate: (Provider) -> Unit
) {
    var apiKey by remember { mutableStateOf(provider.apiKey ?: "") }
    var isKeyVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = provider.name.first().toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = provider.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = provider.baseUrl,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // API Key 输入
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                placeholder = { Text("输入 API Key") },
                singleLine = true,
                visualTransformation = if (isKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { /* TODO: 粘贴 */ }) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "粘贴"
                            )
                        }
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isKeyVisible) "隐藏" else "显示"
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 保存按钮
            Button(
                onClick = {
                    onUpdate(provider.copy(apiKey = apiKey, isConfigured = apiKey.isNotBlank()))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存配置")
            }
        }
    }
}

private fun getMockProviders(): List<Provider> {
    return listOf(
        Provider(
            id = "deepseek",
            name = "DeepSeek",
            icon = "deepseek",
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-xxxxxxxxxxxxxxxx",
            isConfigured = true
        ),
        Provider(
            id = "openai",
            name = "OpenAI",
            icon = "openai",
            baseUrl = "https://api.openai.com",
            apiKey = null,
            isConfigured = false
        ),
        Provider(
            id = "anthropic",
            name = "Anthropic",
            icon = "anthropic",
            baseUrl = "https://api.anthropic.com",
            apiKey = null,
            isConfigured = false
        ),
        Provider(
            id = "openrouter",
            name = "OpenRouter",
            icon = "openrouter",
            baseUrl = "https://openrouter.ai/api",
            apiKey = null,
            isConfigured = false
        ),
        Provider(
            id = "ollama",
            name = "Ollama (本地)",
            icon = "ollama",
            baseUrl = "http://localhost:11434",
            apiKey = null,
            isConfigured = true
        )
    )
}
