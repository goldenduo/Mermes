package com.mermes.core.deb

import android.content.Context
import com.mermes.common.log.MermesLog as Log
import com.mermes.core.Arch
import com.mermes.core.MermesPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Deb package installer — loads preset deb files from native JNI SO
 */
object DebInstaller {
    private const val TAG = "DebInstaller"
    private const val INSTALLED_PACKAGES_FILE = "installed_packages.txt"

    /**
     * Check if all preset packages are already installed
     */
    fun isAllPresetInstalled(context: Context): Boolean {
        val presetNames = getPresetPackageNames(context)
        if (presetNames.isEmpty()) return false
        val installed = getInstalledPackages(context)
        return presetNames.all { installed.containsKey(it) }
    }

    /**
     * Install all preset deb packages
     */
    suspend fun installPresetPackages(
        context: Context,
        progressCallback: ((packageName: String, current: Int, total: Int) -> Unit)? = null
    ): List<DebInstallResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DebInstallResult>()
        val prefixDir = MermesPaths.getPrefixDir(context)

        if (!prefixDir.exists()) {
            Log.e(TAG, "Prefix directory does not exist, bootstrap not installed")
            return@withContext results
        }

        val presetPackages = getPresetPackageNames(context)
        if (presetPackages.isEmpty()) {
            Log.i(TAG, "No preset packages found in native SO")
            return@withContext results
        }

        val packageOrder = try {
            resolvePackageOrder(context, presetPackages)
        } catch (e: CircularDependencyException) {
            Log.e(TAG, "Circular dependency detected: ${e.cycle}", e)
            presetPackages.sorted()
        }

        val total = packageOrder.size
        var current = 0

        for (packageName in packageOrder) {
            current++
            progressCallback?.invoke(packageName, current, total)

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

            val result = installPackageByName(context, packageName)
            results.add(result)

            if (result.success) {
                Log.i(TAG, "[$current/$total] Installed $packageName ${result.version} (${result.installedFiles} files)")
            } else {
                Log.e(TAG, "[$current/$total] Failed to install $packageName: ${result.error}")
            }
        }

        saveInstalledPackages(context, results)
        results
    }

    /**
     * Install a single deb package from byte data
     */
    suspend fun installPackage(
        context: Context,
        debData: ByteArray,
        packageName: String
    ): DebInstallResult = withContext(Dispatchers.IO) {
        installPackageSync(context, debData, packageName)
    }

    /**
     * Get list of installed packages
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
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return getInstalledPackages(context).containsKey(packageName)
    }

    /**
     * Get preset package names by listing deb files in JNI SO ZIP
     */
    fun getPresetPackageNames(context: Context): List<String> {
        return try {
            val zipBytes = NativeDebLib.getZip()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                val names = mutableListOf<String>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.endsWith(".deb")) {
                        names.add(entry.name.substringBefore("_"))
                    }
                }
                names.distinct()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list preset packages from JNI SO", e)
            emptyList()
        }
    }

    /**
     * Get preset package order (dependencies first)
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
     * Resolve package installation order by parsing control files from JNI SO ZIP
     */
    private fun resolvePackageOrder(context: Context, packageNames: List<String>): List<String> {
        val controlList = mutableListOf<DebControl>()

        for (name in packageNames) {
            try {
                val debData = readDebFromJni(name)
                if (debData != null) {
                    val debPackage = DebParser.parse(debData)
                    controlList.add(debPackage.control)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse package $name for dependency resolution", e)
            }
        }

        return DependencyResolver.resolveInstallationOrder(controlList)
    }

    /**
     * Install package by name from JNI SO ZIP
     */
    private fun installPackageByName(context: Context, packageName: String): DebInstallResult {
        return try {
            val debData = readDebFromJni(packageName)
                ?: return DebInstallResult(
                    packageName = packageName,
                    version = "",
                    success = false,
                    installedFiles = 0,
                    error = "Package not found in native JNI SO for ${Arch.current()}"
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
     * Read deb file bytes from JNI SO ZIP by package name
     */
    private fun readDebFromJni(packageName: String): ByteArray? {
        return try {
            val zipBytes = NativeDebLib.getZip()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.endsWith(".deb") && entry.name.startsWith("${packageName}_")) {
                        return zip.readBytes()
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read deb from native SO for $packageName", e)
            null
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

            val debPackage = DebParser.parse(debData)
            val installedFiles = DebParser.extractData(debPackage.dataData, prefixDir)

            try {
                DebParser.executeScripts(debPackage.controlData, prefixDir, "install")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to execute scripts for $packageName", e)
            }

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

    private fun markPackageInstalled(context: Context, packageName: String, version: String) {
        val file = getInstalledPackagesFile(context)
        val entry = "$packageName|$version"

        val existing = if (file.exists()) {
            file.readLines().toMutableList()
        } else {
            mutableListOf()
        }

        val index = existing.indexOfFirst { it.startsWith("$packageName|") }
        if (index >= 0) {
            existing[index] = entry
        } else {
            existing.add(entry)
        }

        file.writeText(existing.joinToString("\n"))
    }

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

    private fun getInstalledPackagesFile(context: Context): File {
        val etcDir = File(MermesPaths.getPrefixDir(context), "etc")
        val mermesDir = File(etcDir, "mermes")
        if (!mermesDir.exists()) {
            mermesDir.mkdirs()
        }
        return File(mermesDir, INSTALLED_PACKAGES_FILE)
    }
}
