package com.mermes.app.data.repository

import com.mermes.app.data.model.ScheduleJob

/**
 * 定时任务仓库接口
 */
interface ScheduleRepository {
    // 获取所有定时任务
    suspend fun getSchedules(): List<ScheduleJob>

    // 切换任务状态
    suspend fun toggleSchedule(id: String, active: Boolean): Boolean

    // 立即触发任务
    suspend fun triggerSchedule(id: String): Boolean

    // 创建任务
    suspend fun createSchedule(cronExpression: String, platform: String, prompt: String): ScheduleJob?

    // 删除任务
    suspend fun deleteSchedule(id: String): Boolean
}
