package com.mermes.core.bootstrap

import android.content.Context
import com.mermes.common.log.MermesLog as Log
import com.mermes.core.MermesPaths
import com.mermes.core.utils.FileUtils
import com.mermes.core.utils.NativeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bootstrap installer - similar to Termux's TermuxInstaller
 */
object MermesBootstrap {
    private const val TAG = "MermesBootstrap"

    // Directories that should have executable permissions
    private val EXECUTABLE_PREFIXES = listOf(
        "bin/",
        "libexec/",
        "lib/apt/apt-helper",
        "lib/apt/methods"
    )

    /**
     * Check if bootstrap is already installed
     *
     * @param context Android Context
     * @return true if installed and valid
     */
    fun isBootstrapInstalled(context: Context): Boolean {
        val prefixDir = MermesPaths.getPrefixDir(context)
        val binDir = MermesPaths.getBinDir(context)

        // Check if prefix directory exists and has content
        if (!prefixDir.exists() || !prefixDir.isDirectory) return false
        if (!binDir.exists() || !binDir.isDirectory) return false

        // Check for essential files
        val bashFile = File(binDir, "bash")
        return bashFile.exists() && bashFile.canExecute()
    }

    /**
     * Get PREFIX directory path
     */
    fun getPrefixDir(context: Context): File = MermesPaths.getPrefixDir(context)

    /**
     * Get HOME directory path
     */
    fun getHomeDir(context: Context): File = MermesPaths.getHomeDir(context)

    /**
     * Clear bootstrap environment (for reinstall)
     */
    fun clearBootstrap(context: Context) {
        Log.i(TAG, "Clearing bootstrap environment")
        FileUtils.deleteRecursive(MermesPaths.getPrefixDir(context))
        FileUtils.deleteRecursive(MermesPaths.getHomeDir(context))
        FileUtils.deleteRecursive(MermesPaths.getStagingDir(context))
    }

    /**
     * Install bootstrap environment
     *
     * @param context Android Context
     * @param progressCallback Progress callback (0.0 - 1.0)
     * @return Installation result
     * @throws BootstrapInstallException on failure
     */
    suspend fun installBootstrap(
        context: Context,
        progressCallback: ((Float) -> Unit)? = null
    ): BootstrapResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Check if already installed
            if (isBootstrapInstalled(context)) {
                Log.i(TAG, "Bootstrap already installed, skipping")
                return@withContext BootstrapResult(
                    success = true,
                    duration = 0,
                    extractedFiles = 0,
                    createdSymlinks = 0
                )
            }

            Log.i(TAG, "Starting bootstrap installation")
            progressCallback?.invoke(0.0f)

            // Step 1: Clean up existing directories
            Log.i(TAG, "Step 1: Cleaning up existing directories")
            val stagingDir = MermesPaths.getStagingDir(context)
            val prefixDir = MermesPaths.getPrefixDir(context)
            val homeDir = MermesPaths.getHomeDir(context)

            FileUtils.deleteRecursive(stagingDir)
            FileUtils.deleteRecursive(prefixDir)

            // Create directories
            FileUtils.createDir(stagingDir)
            FileUtils.createDir(homeDir)
            progressCallback?.invoke(0.1f)

            // Step 2: Load native library and get zip
            Log.i(TAG, "Step 2: Loading bootstrap zip")
            val zipBytes = NativeBootstrapLib.getZip()
            Log.i(TAG, "Bootstrap zip size: ${zipBytes.size} bytes")
            progressCallback?.invoke(0.2f)

            // Step 3: Extract zip to staging directory
            Log.i(TAG, "Step 3: Extracting zip to staging directory")
            var extractedFiles = 0
            extractedFiles = FileUtils.extractZip(zipBytes, stagingDir) { name ->
                // Skip SYMLINKS.txt, we'll process it separately
                name != "SYMLINKS.txt"
            }
            Log.i(TAG, "Extracted $extractedFiles files")
            progressCallback?.invoke(0.5f)

            // Step 4: Set executable permissions
            Log.i(TAG, "Step 4: Setting executable permissions")
            setExecutablePermissions(stagingDir)
            progressCallback?.invoke(0.6f)

            // Step 5: Process symlinks
            Log.i(TAG, "Step 5: Creating symlinks")
            val symlinks = FileUtils.parseSymlinksFile(zipBytes)
            var createdSymlinks = 0
            for ((target, linkPath) in symlinks) {
                val linkFile = File(stagingDir, linkPath)
                try {
                    // Create parent directory if needed
                    FileUtils.createDir(linkFile.parentFile!!)
                    NativeUtils.symlink(target, linkFile.absolutePath)
                    createdSymlinks++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create symlink: $target -> $linkPath", e)
                }
            }
            Log.i(TAG, "Created $createdSymlinks symlinks")
            progressCallback?.invoke(0.8f)

            // Step 6: Atomic rename staging to prefix
            Log.i(TAG, "Step 6: Moving staging to prefix directory")
            Log.d(TAG, "  stagingDir: ${stagingDir.absolutePath}, exists=${stagingDir.exists()}, canWrite=${stagingDir.parentFile?.canWrite()}")
            Log.d(TAG, "  prefixDir:  ${prefixDir.absolutePath}, exists=${prefixDir.exists()}")
            if (prefixDir.exists()) {
                Log.w(TAG, "  prefixDir already exists, deleting before rename")
                prefixDir.deleteRecursively()
            }
            if (!stagingDir.renameTo(prefixDir)) {
                // Fallback: copy + delete (renameTo fails across different mount points)
                Log.w(TAG, "  renameTo failed, trying copy+delete fallback")
                stagingDir.copyRecursively(prefixDir, overwrite = true)
                stagingDir.deleteRecursively()
                if (!prefixDir.exists()) {
                    throw BootstrapInstallException("Failed to move staging directory to prefix (copy fallback also failed)")
                }
                Log.i(TAG, "  copy+delete fallback succeeded")
            }
            progressCallback?.invoke(0.9f)

            // Step 7: Write environment file
            Log.i(TAG, "Step 7: Writing environment file")
            writeEnvironmentFile(context)
            progressCallback?.invoke(1.0f)

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Bootstrap installation completed in ${duration}ms")

            BootstrapResult(
                success = true,
                duration = duration,
                extractedFiles = extractedFiles,
                createdSymlinks = createdSymlinks
            )

        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap installation failed", e)
            // Clean up on failure
            FileUtils.deleteRecursive(MermesPaths.getStagingDir(context))

            val duration = System.currentTimeMillis() - startTime
            BootstrapResult(
                success = false,
                duration = duration,
                extractedFiles = 0,
                createdSymlinks = 0,
                error = e.message
            )
        }
    }

    /**
     * Set executable permissions on bin/, libexec/, etc.
     */
    private fun setExecutablePermissions(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(dir).path
                if (EXECUTABLE_PREFIXES.any { relativePath.startsWith(it) }) {
                    try {
                        NativeUtils.chmod(file.absolutePath, 448) // 0700 in decimal
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set executable permission: $relativePath", e)
                    }
                }
            }
        }
    }

    /**
     * Write environment variables file
     */
    private fun writeEnvironmentFile(context: Context) {
        val prefixDir = MermesPaths.getPrefixDir(context)
        val homeDir = MermesPaths.getHomeDir(context)
        val etcDir = File(prefixDir, "etc")
        val termuxEnvDir = File(etcDir, "termux")
        FileUtils.createDir(termuxEnvDir)

        val envFile = File(termuxEnvDir, "termux.env")
        val envContent = buildString {
            appendLine("export HOME=${homeDir.absolutePath}")
            appendLine("export PREFIX=${prefixDir.absolutePath}")
            appendLine("export PATH=${prefixDir.absolutePath}/bin")
            appendLine("export TMPDIR=${prefixDir.absolutePath}/tmp")
            appendLine("export TERM=xterm-256color")
            appendLine("export LANG=en_US.UTF-8")
        }

        envFile.writeText(envContent)
        Log.i(TAG, "Environment file written to: ${envFile.absolutePath}")
    }
}
