package com.mermes.app.data.repository

import com.mermes.app.data.model.Kanban
import com.mermes.app.data.model.KanbanTask

/**
 * 看板仓库接口
 */
interface KanbanRepository {
    // 获取所有看板
    suspend fun getKanbans(): List<Kanban>

    // 创建任务
    suspend fun createTask(title: String, description: String? = null): KanbanTask?

    // 指派任务
    suspend fun assignTask(taskId: String, assignee: String): Boolean

    // 阻塞任务
    suspend fun blockTask(taskId: String, reason: String): Boolean

    // 完成任务
    suspend fun completeTask(taskId: String, result: String? = null): Boolean

    // 调度任务
    suspend fun dispatch(): Boolean
}
