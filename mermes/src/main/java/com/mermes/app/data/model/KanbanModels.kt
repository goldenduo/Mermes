package com.mermes.app.data.model

/**
 * 看板实体
 */
data class Kanban(
    val slug: String,                   // 看板标识
    val title: String,                  // 看板标题
    val tasks: List<KanbanTask>         // 任务列表
)

/**
 * 看板任务
 */
data class KanbanTask(
    val id: String,                     // 任务 ID
    val title: String,                  // 任务标题
    val description: String?,           // 任务描述
    val status: TaskStatus,             // 任务状态
    val assignee: String?,              // 负责人
    val isBlocked: Boolean = false,     // 是否被阻塞
    val blockReason: String? = null,    // 阻塞原因
    val createdAt: Long,                // 创建时间
    val updatedAt: Long                 // 更新时间
)

/**
 * 任务状态
 */
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    REVIEW,
    DONE,
    ARCHIVED
}
