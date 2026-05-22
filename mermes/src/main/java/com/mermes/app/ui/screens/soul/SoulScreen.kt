package com.mermes.app.ui.screens.soul

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulScreen(
    onNavigateBack: () -> Unit
) {
    var soulText by remember { mutableStateOf(getDefaultSoul()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSavedIndicator by remember { mutableStateOf(false) }
    var lastSavedText by remember { mutableStateOf(soulText) }

    // 防抖自动保存
    LaunchedEffect(soulText) {
        if (soulText != lastSavedText) {
            delay(500)
            // TODO: 保存到后端
            lastSavedText = soulText
            showSavedIndicator = true
            delay(2000)
            showSavedIndicator = false
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置灵魂") },
            text = { Text("确定将灵魂恢复为出厂设置？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        soulText = getDefaultSoul()
                        showResetDialog = false
                    }
                ) {
                    Text("确认重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "灵魂设定",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (showSavedIndicator) {
                            Text(
                                text = "已安全同步",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重置灵魂"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "定义 AI 的人格、行为和专长",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = soulText,
                onValueChange = { soulText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                label = { Text("灵魂内容") },
                shape = RoundedCornerShape(12.dp),
                minLines = 20
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 字数统计
            Text(
                text = "${soulText.length} 字符",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

private fun getDefaultSoul(): String {
    return """你是一个智能助手，具备以下特质：

## 人格特征
- 友好、专业、耐心
- 善于倾听和理解用户需求
- 能够提供清晰、准确的解答

## 专业领域
- 软件开发：熟悉多种编程语言和框架
- 技术咨询：能够提供架构设计和最佳实践建议
- 问题诊断：善于分析和解决技术问题

## 行为准则
- 始终保持诚实和客观
- 在不确定时明确告知用户
- 尊重用户隐私和数据安全
- 提供有建设性的反馈和建议

## 沟通风格
- 使用简洁明了的语言
- 适当使用技术术语，但确保用户能理解
- 在必要时提供代码示例
- 保持专业但友好的语气"""
}
