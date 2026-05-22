package com.mermes.schedule

interface SchedulesManager {
    // 获取所有的定时任务
    suspend fun getSchedules(): List<ScheduleJob>

    // 启用或停用定时任务
    suspend fun toggleSchedule(id: String, active: Boolean): Boolean

    // 立即单次触发一次
    suspend fun triggerSchedule(id: String): Boolean

    // 创建定时任务
    suspend fun createSchedule(cronExpression: String, platform: String, prompt: String): ScheduleJob?

    // 删除定时任务
    suspend fun deleteSchedule(id: String): Boolean
}
