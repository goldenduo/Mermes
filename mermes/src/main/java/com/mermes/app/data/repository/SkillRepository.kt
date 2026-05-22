package com.mermes.app.data.repository

import com.mermes.app.data.model.Skill

/**
 * 技能仓库接口
 */
interface SkillRepository {
    // 获取所有技能
    suspend fun getSkills(): List<Skill>

    // 扫描本地技能
    suspend fun scanLocalSkills(): List<Skill>

    // 安装技能
    suspend fun installSkill(name: String): Boolean

    // 卸载技能
    suspend fun uninstallSkill(name: String): Boolean
}
