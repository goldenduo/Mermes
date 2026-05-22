package com.mermes.app.ui.screens.models

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mermes.app.data.model.AiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    onNavigateBack: () -> Unit
) {
    var models by remember { mutableStateOf(getMockModels()) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "模型库",
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: 添加模型 */ }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加模型"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索模型...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            // 模型网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                val filteredModels = if (searchQuery.isBlank()) {
                    models
                } else {
                    models.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                                it.id.contains(searchQuery, ignoreCase = true)
                    }
                }

                items(filteredModels) { model ->
                    ModelCard(
                        model = model,
                        onSetDefault = {
                            models = models.map {
                                it.copy(isDefault = it.id == model.id)
                            }
                        },
                        onDelete = {
                            models = models.filter { it.id != model.id }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: AiModel,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSetDefault),
        colors = CardDefaults.cardColors(
            containerColor = if (model.isDefault) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ModelTraining,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (model.isDefault) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "默认",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = model.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = model.id,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = model.provider,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun getMockModels(): List<AiModel> {
    return listOf(
        AiModel(
            id = "deepseek-chat",
            name = "DeepSeek Chat",
            provider = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            isDefault = true,
            maxTokens = 4096,
            supportsVision = false,
            supportsTools = true
        ),
        AiModel(
            id = "gpt-4",
            name = "GPT-4",
            provider = "OpenAI",
            baseUrl = "https://api.openai.com",
            isDefault = false,
            maxTokens = 8192,
            supportsVision = true,
            supportsTools = true
        ),
        AiModel(
            id = "claude-3-opus",
            name = "Claude 3 Opus",
            provider = "Anthropic",
            baseUrl = "https://api.anthropic.com",
            isDefault = false,
            maxTokens = 4096,
            supportsVision = true,
            supportsTools = true
        ),
        AiModel(
            id = "llama3:8b",
            name = "Llama 3 8B",
            provider = "Ollama",
            baseUrl = "http://localhost:11434",
            isDefault = false,
            maxTokens = 2048,
            supportsVision = false,
            supportsTools = false
        ),
        AiModel(
            id = "qwen2.5:7b",
            name = "Qwen 2.5 7B",
            provider = "Ollama",
            baseUrl = "http://localhost:11434",
            isDefault = false,
            maxTokens = 2048,
            supportsVision = false,
            supportsTools = false
        )
    )
}
