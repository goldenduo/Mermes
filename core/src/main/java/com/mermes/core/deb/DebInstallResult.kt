package com.mermes.core.deb

/**
 * Deb package installation result
 */
data class DebInstallResult(
    val packageName: String,
    val version: String,
    val success: Boolean,
    val installedFiles: Int,
    val error: String? = null
)
