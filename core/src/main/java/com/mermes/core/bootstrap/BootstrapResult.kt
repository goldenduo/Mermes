package com.mermes.core.bootstrap

/**
 * Bootstrap installation result
 */
data class BootstrapResult(
    val success: Boolean,
    val duration: Long, // Duration in milliseconds
    val extractedFiles: Int, // Number of extracted files
    val createdSymlinks: Int, // Number of created symlinks
    val error: String? = null
)

/**
 * Bootstrap installation exception
 */
class BootstrapInstallException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
