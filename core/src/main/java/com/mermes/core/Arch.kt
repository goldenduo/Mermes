package com.mermes.core

import android.content.Context
import java.io.File

/**
 * Supported architectures
 */
enum class Arch(val value: String) {
    AARCH64("aarch64"),
    ARM("arm"),
    I686("i686"),
    X86_64("x86_64");

    companion object {
        /**
         * Get current architecture from system properties
         */
        fun current(): Arch {
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            return when (abi) {
                "arm64-v8a" -> AARCH64
                "armeabi-v7a", "armeabi" -> ARM
                "x86_64" -> X86_64
                "x86" -> I686
                else -> throw IllegalStateException("Unsupported architecture: $abi")
            }
        }
    }
}

/**
 * Path constants for Mermes environment
 */
object MermesPaths {
    const val PREFIX_DIR_NAME = "usr"
    const val HOME_DIR_NAME = "home"
    const val STAGING_DIR_NAME = "usr-staging"
    const val SYMLINKS_FILE = "SYMLINKS.txt"

    fun getPrefixDir(context: Context): File =
        File(context.filesDir, PREFIX_DIR_NAME)

    fun getHomeDir(context: Context): File =
        File(context.filesDir, HOME_DIR_NAME)

    fun getStagingDir(context: Context): File =
        File(context.filesDir, STAGING_DIR_NAME)

    fun getBinDir(context: Context): File =
        File(getPrefixDir(context), "bin")

    fun getTmpDir(context: Context): File =
        File(getPrefixDir(context), "tmp")

    fun getUsrDir(context: Context): File =
        getPrefixDir(context)

    fun getEtcDir(context: Context): File =
        File(getPrefixDir(context), "etc")
}
