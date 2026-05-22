package com.mermes.schedule

data class ScheduleJob(
    val id: String,                  // 任务唯一识别 Slug / UUID
    val cronExpression: String,      // Cron 表达式 (如 "0 9 * * 1-5")
    val platform: String,            // 投递目标平台名称 (如 "Telegram", "Feishu")
    val prompt: String,              // 投递触发提示词或生成的系统 Prompt
    val isActive: Boolean,           // 调度是否激活
    val lastRunTime: Long,           // 上一次执行成功时间戳
    val nextRunTime: Long,           // 预估下一次执行时间戳
    val lastError: String? = null    // 上一次运行错误堆栈摘要 (如有)
)
