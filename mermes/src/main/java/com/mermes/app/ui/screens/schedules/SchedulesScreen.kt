package com.mermes.app.ui.screens.schedules

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mermes.app.data.model.ScheduleJob
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    onNavigateBack: () -> Unit,
    onCreateNew: () -> Unit
) {
    var schedules by remember { mutableStateOf(getMockSchedules()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "定时任务",
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
            FloatingActionButton(onClick = onCreateNew) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建任务"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(schedules) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onToggle = { active ->
                        schedules = schedules.map {
                            if (it.id == schedule.id) it.copy(isActive = active) else it
                        }
                    },
                    onTrigger = { /* TODO: 立即触发 */ },
                    onDelete = {
                        schedules = schedules.filter { it.id != schedule.id }
                    }
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: ScheduleJob,
    onToggle: (Boolean) -> Unit,
    onTrigger: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

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
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (schedule.isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = schedule.platform,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = schedule.cronExpression,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 提示词预览
            Text(
                text = schedule.prompt,
                fontSize = 14.sp,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 时间信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "上次运行",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = dateFormat.format(Date(schedule.lastRunTime)),
                        fontSize = 12.sp
                    )
                }
                Column {
                    Text(
                        text = "下次运行",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = dateFormat.format(Date(schedule.nextRunTime)),
                        fontSize = 12.sp
                    )
                }
            }

            // 错误信息
            if (schedule.lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = schedule.lastError,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { onToggle(!schedule.isActive) }) {
                    Icon(
                        imageVector = if (schedule.isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (schedule.isActive) "暂停" else "启用",
                        tint = if (schedule.isActive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onTrigger) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "立即执行",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun getMockSchedules(): List<ScheduleJob> {
    return listOf(
        ScheduleJob(
            id = "job_1",
            cronExpression = "0 9 * * 1-5",
            platform = "Telegram",
            prompt = "生成今日工作日报摘要",
            isActive = true,
            lastRunTime = System.currentTimeMillis() - 86400000,
            nextRunTime = System.currentTimeMillis() + 3600000,
            lastError = null
        ),
        ScheduleJob(
            id = "job_2",
            cronExpression = "0 18 * * 5",
            platform = "飞书",
            prompt = "汇总本周项目进展并生成周报",
            isActive = true,
            lastRunTime = System.currentTimeMillis() - 172800000,
            nextRunTime = System.currentTimeMillis() + 7200000,
            lastError = null
        ),
        ScheduleJob(
            id = "job_3",
            cronExpression = "*/30 * * * *",
            platform = "Discord",
            prompt = "检查服务器状态并报告",
            isActive = false,
            lastRunTime = System.currentTimeMillis() - 3600000,
            nextRunTime = System.currentTimeMillis(),
            lastError = "Connection timeout: Unable to reach server"
        )
    )
}
