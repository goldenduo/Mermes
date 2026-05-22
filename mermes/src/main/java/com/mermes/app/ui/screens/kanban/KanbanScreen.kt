package com.mermes.app.ui.screens.kanban

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mermes.app.data.model.KanbanTask
import com.mermes.app.data.model.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanScreen(
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("待办", "进行中", "审核", "已完成")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "任务看板",
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
            Column {
                FloatingActionButton(
                    onClick = { /* TODO: Dispatch */ }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "调度"
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                FloatingActionButton(
                    onClick = { /* TODO: 新建任务 */ }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建任务"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 泳道标签
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items(tabs.size) { index ->
                    TabItem(
                        title = tabs[index],
                        isSelected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }

            // 任务卡片列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                val mockTasks = getMockTasks()
                val filteredTasks = when (selectedTab) {
                    0 -> mockTasks.filter { it.status == TaskStatus.TODO }
                    1 -> mockTasks.filter { it.status == TaskStatus.IN_PROGRESS }
                    2 -> mockTasks.filter { it.status == TaskStatus.REVIEW }
                    3 -> mockTasks.filter { it.status == TaskStatus.DONE }
                    else -> mockTasks
                }

                items(filteredTasks) { task ->
                    TaskCard(task = task)
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .let { it },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TaskCard(task: KanbanTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (task.isBlocked) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (task.description != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = task.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (task.assignee != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = task.assignee.first().toString(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.assignee,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (task.isBlocked && task.blockReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "阻塞: ${task.blockReason}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun getMockTasks(): List<KanbanTask> {
    return listOf(
        KanbanTask(
            id = "1",
            title = "实现用户认证模块",
            description = "添加 JWT 认证支持",
            status = TaskStatus.TODO,
            assignee = "Alice",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ),
        KanbanTask(
            id = "2",
            title = "优化数据库查询",
            description = "减少 N+1 查询问题",
            status = TaskStatus.IN_PROGRESS,
            assignee = "Bob",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ),
        KanbanTask(
            id = "3",
            title = "修复登录页面 bug",
            description = null,
            status = TaskStatus.IN_PROGRESS,
            assignee = null,
            isBlocked = true,
            blockReason = "等待设计稿更新",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ),
        KanbanTask(
            id = "4",
            title = "编写单元测试",
            description = "覆盖核心业务逻辑",
            status = TaskStatus.REVIEW,
            assignee = "Charlie",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ),
        KanbanTask(
            id = "5",
            title = "部署到生产环境",
            description = null,
            status = TaskStatus.DONE,
            assignee = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    )
}
