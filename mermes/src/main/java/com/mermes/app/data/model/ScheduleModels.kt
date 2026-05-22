package com.mermes.app.data.model

/**
 * 定时任务实体
 */
data class ScheduleJob(
    val id: String,                     // 任务唯一标识
    val cronExpression: String,         // Cron 表达式
    val platform: String,               // 投递目标平台
    val prompt: String,                 // 触发提示词
    val isActive: Boolean,              // 是否激活
    val lastRunTime: Long,              // 上一次执行时间戳
    val nextRunTime: Long,              // 下一次执行时间戳
    val lastError: String? = null       // 上一次运行错误
)
