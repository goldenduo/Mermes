package com.mermes.app.data.model

/**
 * 技能/插件实体
 */
data class Skill(
    val name: String,                   // 技能名称
    val description: String,            // 技能描述
    val path: String,                   // 安装路径
    val isInstalled: Boolean = true,    // 是否已安装
    val version: String? = null         // 版本号
)
