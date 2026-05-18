package com.mermes.core.utils

/**
 * JNI Native methods for file operations and system utilities
 */
internal object NativeUtils {

    /**
     * Load native library
     */
    fun load() {
        System.loadLibrary("mermes-utils")
    }

    /**
     * Set file permissions
     *
     * @param path File path
     * @param mode Permission mode (e.g., 0700)
     */
    external fun chmod(path: String, mode: Int)

    /**
     * Create a symbolic link
     *
     * @param target Link target
     * @param linkPath Link path
     */
    external fun symlink(target: String, linkPath: String)

    /**
     * Get current device architecture
     *
     * @return Architecture name (aarch64, arm, i686, x86_64)
     */
    external fun getArch(): String

    /**
     * Check if a file is an ELF binary
     *
     * @param path File path
     * @return true if ELF binary
     */
    external fun isElfBinary(path: String): Boolean

    /**
     * Get the shebang interpreter path from a script file
     *
     * @param path File path
     * @return Interpreter path, or null if no shebang
     */
    external fun getShebang(path: String): String?

    init {
        load()
    }
}
