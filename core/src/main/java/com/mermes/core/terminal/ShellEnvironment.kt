package com.mermes.core.terminal

import android.content.Context
import com.mermes.core.MermesPaths
import java.io.File

/**
 * Shell environment variable management
 */
object ShellEnvironment {

    /**
     * Get standard Termux-compatible environment variables
     *
     * @param context Android Context
     * @return Environment variables Map
     */
    fun getEnvironment(context: Context): Map<String, String> {
        val prefixDir = MermesPaths.getPrefixDir(context)
        val homeDir = MermesPaths.getHomeDir(context)
        val tmpDir = MermesPaths.getTmpDir(context)

        return buildMap {
            put("HOME", homeDir.absolutePath)
            put("PREFIX", prefixDir.absolutePath)
            put("PATH", "${prefixDir.absolutePath}/bin")
            put("TMPDIR", tmpDir.absolutePath)
            put("TERM", "xterm-256color")
            put("LANG", "en_US.UTF-8")
            put("LC_ALL", "en_US.UTF-8")
            put("SHELL", "${prefixDir.absolutePath}/bin/bash")

            // Termux-compatible app info
            put("TERMUX_VERSION", "1.0")
            put("TERMUX_APP__PACKAGE_NAME", "com.mermes")
            put("TERMUX_APP__FILES_DIR", context.filesDir.absolutePath)

            // Android specific
            put("ANDROID_DATA", "/data")
            put("ANDROID_ROOT", "/system")
        }
    }

    /**
     * Get PATH value
     *
     * @param context Android Context
     * @return PATH string
     */
    fun getPath(context: Context): String {
        return "${MermesPaths.getPrefixDir(context).absolutePath}/bin"
    }

    /**
     * Get environment as array for JNI
     *
     * @param context Android Context
     * @return Environment array ["KEY=VALUE", ...]
     */
    fun getEnvironmentArray(context: Context): Array<String> {
        return getEnvironment(context).map { "${it.key}=${it.value}" }.toTypedArray()
    }

    /**
     * Write environment variables to file
     *
     * @param context Android Context
     */
    fun writeEnvironmentToFile(context: Context) {
        val prefixDir = MermesPaths.getPrefixDir(context)
        val homeDir = MermesPaths.getHomeDir(context)
        val etcDir = File(prefixDir, "etc")
        val termuxEnvDir = File(etcDir, "termux")

        if (!termuxEnvDir.exists()) {
            termuxEnvDir.mkdirs()
        }

        val envFile = File(termuxEnvDir, "termux.env")
        val envContent = buildString {
            appendLine("# Mermes environment variables")
            appendLine("export HOME=${homeDir.absolutePath}")
            appendLine("export PREFIX=${prefixDir.absolutePath}")
            appendLine("export PATH=${prefixDir.absolutePath}/bin")
            appendLine("export TMPDIR=${prefixDir.absolutePath}/tmp")
            appendLine("export TERM=xterm-256color")
            appendLine("export LANG=en_US.UTF-8")
            appendLine("export LC_ALL=en_US.UTF-8")
            appendLine("export SHELL=${prefixDir.absolutePath}/bin/bash")
        }

        envFile.writeText(envContent)
    }

    /**
     * Setup default bash profile
     *
     * @param context Android Context
     */
    fun setupDefaultProfile(context: Context) {
        val homeDir = MermesPaths.getHomeDir(context)
        if (!homeDir.exists()) {
            homeDir.mkdirs()
        }

        // Create .bash_profile
        val bashProfile = File(homeDir, ".bash_profile")
        if (!bashProfile.exists()) {
            bashProfile.writeText(buildString {
                appendLine("# Load .bashrc if it exists")
                appendLine("if [ -f ~/.bashrc ]; then")
                appendLine("    . ~/.bashrc")
                appendLine("fi")
            })
        }

        // Create .bashrc
        val bashrc = File(homeDir, ".bashrc")
        if (!bashrc.exists()) {
            bashrc.writeText(buildString {
                appendLine("# Mermes bash configuration")
                appendLine("PS1='\\[\\033[1;32m\\]\\u@mermes\\[\\033[0m\\]:\\[\\033[1;34m\\]\\w\\[\\033[0m\\]\\$ '")
                appendLine("")
                appendLine("# Aliases")
                appendLine("alias ls='ls --color=auto'")
                appendLine("alias ll='ls -la'")
                appendLine("alias la='ls -A'")
            })
        }
    }
}
