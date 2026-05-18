package com.mermes.core.deb

import android.content.Context
import android.util.Log
import com.mermes.core.Arch
import com.mermes.core.MermesPaths
import com.mermes.core.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deb package installer
 */
object DebInstaller {
    private const val TAG = "DebInstaller"
    private const val INSTALLED_PACKAGES_FILE = "installed_packages.txt"

    /**
     * Install all preset deb packages
     *
     * @param context Android Context
     * @param progressCallback Progress callback (packageName, current, total)
     * @return List of installation results
     */
    suspend fun installPresetPackages(
        context: Context,
        progressCallback: ((packageName: String, current: Int, total: Int) -> Unit)? = null
    ): List<DebInstallResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DebInstallResult>()
        val prefixDir = MermesPaths.getPrefixDir(context)

        // Check if prefix directory exists
        if (!prefixDir.exists()) {
            Log.e(TAG, "Prefix directory does not exist, bootstrap not installed")
            return@withContext results
        }

        // Get list of preset packages
        val presetPackages = getPresetPackageNames(context)
        if (presetPackages.isEmpty()) {
            Log.i(TAG, "No preset packages found")
            return@withContext results
        }

        // Resolve installation order
        val packageOrder = try {
            resolvePackageOrder(context, presetPackages)
        } catch (e: CircularDependencyException) {
            Log.e(TAG, "Circular dependency detected: ${e.cycle}", e)
            // Fall back to alphabetical order
            presetPackages.sorted()
        }

        val total = packageOrder.size
        var current = 0

        // Install packages in order
        for (packageName in packageOrder) {
            current++
            progressCallback?.invoke(packageName, current, total)

            // Skip if already installed
            if (isPackageInstalled(context, packageName)) {
                Log.i(TAG, "Package $packageName already installed, skipping")
                results.add(DebInstallResult(
                    packageName = packageName,
                    version = "already installed",
                    success = true,
                    installedFiles = 0
                ))
                continue
            }

            // Install package
            val result = installPackageByName(context, packageName)
            results.add(result)

            if (!result.success) {
                Log.e(TAG, "Failed to install package $packageName: ${result.error}")
            }
        }

        // Save installed packages list
        saveInstalledPackages(context, results)

        results
    }

    /**
     * Install a single deb package
     *
     * @param context Android Context
     * @param debData Deb file byte array
     * @param packageName Package name (for logging)
     * @return Installation result
     */
    suspend fun installPackage(
        context: Context,
        debData: ByteArray,
        packageName: String
    ): DebInstallResult = withContext(Dispatchers.IO) {
        try {
            val prefixDir = MermesPaths.getPrefixDir(context)

            // Parse deb file
            val debPackage = DebParser.parse(debData)

            // Extract data to prefix directory
            val installedFiles = DebParser.extractData(debPackage.dataData, prefixDir)

            // Execute postinst script if present
            try {
                DebParser.executeScripts(debPackage.controlData, prefixDir, "install")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to execute scripts for $packageName", e)
            }

            // Mark as installed
            markPackageInstalled(context, debPackage.control.packageName, debPackage.control.version)

            DebInstallResult(
                packageName = debPackage.control.packageName,
                version = debPackage.control.version,
                success = true,
                installedFiles = installedFiles
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to install package $packageName", e)
            DebInstallResult(
                packageName = packageName,
                version = "",
                success = false,
                installedFiles = 0,
                error = e.message
            )
        }
    }

    /**
     * Get list of installed packages
     *
     * @param context Android Context
     * @return Map of package name to version
     */
    fun getInstalledPackages(context: Context): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val file = getInstalledPackagesFile(context)

        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.split("|")
                if (parts.size == 2) {
                    result[parts[0]] = parts[1]
                }
            }
        }

        return result
    }

    /**
     * Check if package is installed
     *
     * @param context Android Context
     * @param packageName Package name
     * @return true if installed
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return getInstalledPackages(context).containsKey(packageName)
    }

    /**
     * Get preset package names from native library
     *
     * @param context Android Context
     * @return List of package names
     */
    fun getPresetPackageNames(context: Context): List<String> {
        return try {
            NativeDebLib.getDebNames().toList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get preset package names", e)
            emptyList()
        }
    }

    /**
     * Get preset package order (dependencies first)
     *
     * @param context Android Context
     * @return Ordered list of package names
     */
    fun getPresetPackageOrder(context: Context): List<String> {
        val packageNames = getPresetPackageNames(context)
        return try {
            resolvePackageOrder(context, packageNames)
        } catch (e: CircularDependencyException) {
            Log.e(TAG, "Circular dependency detected", e)
            packageNames.sorted()
        }
    }

    /**
     * Resolve package installation order
     */
    private fun resolvePackageOrder(context: Context, packageNames: List<String>): List<String> {
        val arch = Arch.current().value
        val controlList = mutableListOf<DebControl>()

        // Parse control info from each package
        for (name in packageNames) {
            try {
                val debData = NativeDebLib.getDebByArchAndName(arch, name)
                if (debData != null) {
                    val debPackage = DebParser.parse(debData)
                    controlList.add(debPackage.control)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse package $name", e)
            }
        }

        return DependencyResolver.resolveInstallationOrder(controlList)
    }

    /**
     * Install package by name from native library
     */
    private fun installPackageByName(context: Context, packageName: String): DebInstallResult {
        val arch = Arch.current().value

        return try {
            val debData = NativeDebLib.getDebByArchAndName(arch, packageName)
                ?: return DebInstallResult(
                    packageName = packageName,
                    version = "",
                    success = false,
                    installedFiles = 0,
                    error = "Package not found for architecture $arch"
                )

            installPackageSync(context, debData, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install package $packageName", e)
            DebInstallResult(
                packageName = packageName,
                version = "",
                success = false,
                installedFiles = 0,
                error = e.message
            )
        }
    }

    /**
     * Synchronous package installation
     */
    private fun installPackageSync(
        context: Context,
        debData: ByteArray,
        packageName: String
    ): DebInstallResult {
        try {
            val prefixDir = MermesPaths.getPrefixDir(context)

            // Parse deb file
            val debPackage = DebParser.parse(debData)

            // Extract data to prefix directory
            val installedFiles = DebParser.extractData(debPackage.dataData, prefixDir)

            // Execute postinst script if present
            try {
                DebParser.executeScripts(debPackage.controlData, prefixDir, "install")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to execute scripts for $packageName", e)
            }

            // Mark as installed
            markPackageInstalled(context, debPackage.control.packageName, debPackage.control.version)

            return DebInstallResult(
                packageName = debPackage.control.packageName,
                version = debPackage.control.version,
                success = true,
                installedFiles = installedFiles
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to install package $packageName", e)
            return DebInstallResult(
                packageName = packageName,
                version = "",
                success = false,
                installedFiles = 0,
                error = e.message
            )
        }
    }

    /**
     * Mark package as installed
     */
    private fun markPackageInstalled(context: Context, packageName: String, version: String) {
        val file = getInstalledPackagesFile(context)
        val entry = "$packageName|$version"

        val existing = if (file.exists()) {
            file.readLines().toMutableList()
        } else {
            mutableListOf()
        }

        // Update or add entry
        val index = existing.indexOfFirst { it.startsWith("$packageName|") }
        if (index >= 0) {
            existing[index] = entry
        } else {
            existing.add(entry)
        }

        file.writeText(existing.joinToString("\n"))
    }

    /**
     * Save installed packages list
     */
    private fun saveInstalledPackages(context: Context, results: List<DebInstallResult>) {
        val file = getInstalledPackagesFile(context)
        val successful = results.filter { it.success }

        val existing = getInstalledPackages(context).toMutableMap()
        successful.forEach { result ->
            existing[result.packageName] = result.version
        }

        val lines = existing.map { "${it.key}|${it.value}" }
        file.writeText(lines.joinToString("\n"))
    }

    /**
     * Get installed packages file
     */
    private fun getInstalledPackagesFile(context: Context): File {
        val etcDir = File(MermesPaths.getPrefixDir(context), "etc")
        val mermesDir = File(etcDir, "mermes")
        if (!mermesDir.exists()) {
            mermesDir.mkdirs()
        }
        return File(mermesDir, INSTALLED_PACKAGES_FILE)
    }
}
