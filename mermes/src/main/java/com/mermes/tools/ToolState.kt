package com.mermes.tools

data class ToolState(
    val name: String,                // 工具名称 (如 "file", "web", "terminal")
    val isEnabled: Boolean,          // 是否启用
    val iconResId: String,           // SVG 图标别名
    val isHighRisk: Boolean          // 是否为高风险工具（开启前需触发沙箱警告弹窗）
)
