package com.mermes.core.deb

import com.mermes.common.log.MermesLog as Log
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Deb file parser
 */
internal object DebParser {
    private const val TAG = "DebParser"

    /**
     * Parse deb file
     *
     * @param debData Deb file byte array
     * @return Parsed deb package
     */
    fun parse(debData: ByteArray): DebPackage {
        val arInput = ArArchiveInputStream(ByteArrayInputStream(debData))

        var controlData: ByteArray? = null
        var dataData: ByteArray? = null

        var entry: ArArchiveEntry? = arInput.nextArEntry
        while (entry != null) {
            when {
                entry.name.startsWith("control.tar") -> {
                    controlData = arInput.readBytes()
                }
                entry.name.startsWith("data.tar") -> {
                    dataData = arInput.readBytes()
                }
            }
            entry = arInput.nextArEntry
        }

        arInput.close()

        if (controlData == null || dataData == null) {
            throw DebParseException("Invalid deb file: missing control or data archive")
        }

        val control = parseControl(controlData)

        return DebPackage(
            control = control,
            controlData = controlData,
            dataData = dataData
        )
    }

    /**
     * Parse control.tar.xz to get control information
     */
    fun parseControl(controlData: ByteArray): DebControl {
        val tarInput = TarArchiveInputStream(getDecompressedStream(controlData))

        var entry: TarArchiveEntry? = tarInput.nextTarEntry
        while (entry != null) {
            if (entry.name == "./control" || entry.name == "control") {
                val content = tarInput.readBytes().toString(Charsets.UTF_8)
                tarInput.close()
                return DebControl.parse(content)
            }
            entry = tarInput.nextTarEntry
        }

        tarInput.close()
        throw DebParseException("Control file not found in control archive")
    }

    /**
     * Extract data.tar.xz to target directory
     *
     * @param dataData data.tar.xz byte array
     * @param targetDir Target directory
     * @return Number of extracted files
     */
    fun extractData(dataData: ByteArray, targetDir: File): Int {
        var count = 0
        val tarInput = TarArchiveInputStream(getDecompressedStream(dataData))

        var entry: TarArchiveEntry? = tarInput.nextTarEntry
        while (entry != null) {
            val rawName = entry.name.removePrefix("./")
            // Strip Termux-style prefix: "data/data/<pkg>/files/usr/" → ""
            // This ensures files are extracted relative to $PREFIX, not nested
            val name = stripTermuxPrefix(rawName)

            if (name.isNotEmpty()) {
                val targetFile = File(targetDir, name)

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else if (entry.linkFlag == 50.toByte()) { // 50 = '2' = symlink
                    // Handle symlinks
                    targetFile.parentFile?.mkdirs()
                    if (targetFile.exists() || java.nio.file.Files.isSymbolicLink(targetFile.toPath())) {
                        targetFile.delete()
                    }
                    try {
                        android.system.Os.symlink(entry.linkName, targetFile.absolutePath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create symlink: ${targetFile.absolutePath} -> ${entry.linkName}", e)
                    }
                    count++
                } else {
                    // Ensure parent directory exists
                    targetFile.parentFile?.mkdirs()

                    // Remove existing directory at target path (e.g., dir replaced by symlink in another deb)
                    if (targetFile.isDirectory) {
                        targetFile.deleteRecursively()
                    }

                    // Extract file
                    FileOutputStream(targetFile).use { out ->
                        tarInput.copyTo(out)
                    }

                    // Set permissions
                    try {
                        val mode = entry.mode
                        if (mode > 0) {
                            setFilePermissions(targetFile, mode)
                        }
                    } catch (e: Exception) {
                        // Ignore permission errors
                    }

                    count++
                }
            }

            entry = tarInput.nextTarEntry
        }

        tarInput.close()
        return count
    }

    /**
     * Execute preinst/postinst scripts if present
     */
    fun executeScripts(
        controlData: ByteArray,
        prefixDir: File,
        action: String // "install", "upgrade", "remove"
    ) {
        val tarInput = TarArchiveInputStream(getDecompressedStream(controlData))

        var entry: TarArchiveEntry? = tarInput.nextTarEntry
        while (entry != null) {
            val scriptName = when {
                entry.name.endsWith("preinst") && action in listOf("install", "upgrade") -> "preinst"
                entry.name.endsWith("postinst") && action in listOf("install", "upgrade") -> "postinst"
                entry.name.endsWith("prerm") && action == "remove" -> "prerm"
                entry.name.endsWith("postrm") && action == "remove" -> "postrm"
                else -> null
            }

            if (scriptName != null && entry.isFile) {
                val scriptContent = tarInput.readBytes().toString(Charsets.UTF_8)
                executeScript(scriptContent, prefixDir, action)
            }

            entry = tarInput.nextTarEntry
        }

        tarInput.close()
    }

    /**
     * Execute a single script
     */
    private fun executeScript(script: String, prefixDir: File, action: String) {
        val localSh = File(prefixDir, "bin/sh")
        // 1. 防御性提权
        if (localSh.exists()) {
            localSh.setExecutable(true, false)
        }

        var process: Process? = null
        try {
            // 2. 尝试使用本地 sh 运行，提供完整沙盒环境
            process = ProcessBuilder(
                localSh.absolutePath,
                "-c",
                script
            ).apply {
                directory(prefixDir)
                environment().apply {
                    put("PATH", "${prefixDir.absolutePath}/bin:${prefixDir.absolutePath}/usr/bin:/system/bin")
                    put("LD_LIBRARY_PATH", "${prefixDir.absolutePath}/lib:${prefixDir.absolutePath}/usr/lib")
                    put("DPKG_MAINTSCRIPT_NAME", action)
                    put("DPKG_MAINTSCRIPT_PACKAGE", "package")
                    put("TERMUX_PREFIX", prefixDir.absolutePath)
                }
                redirectErrorStream(true)
            }.start()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to run script with local sh, fallback to system sh: ${e.message}")
            try {
                // 3. 失败时 Fallback 到系统通用 sh 运行，注入本地 PATH 和库路径
                process = ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    script
                ).apply {
                    directory(prefixDir)
                    environment().apply {
                        put("PATH", "${prefixDir.absolutePath}/bin:${prefixDir.absolutePath}/usr/bin:/system/bin")
                        put("LD_LIBRARY_PATH", "${prefixDir.absolutePath}/lib:${prefixDir.absolutePath}/usr/lib")
                        put("DPKG_MAINTSCRIPT_NAME", action)
                        put("DPKG_MAINTSCRIPT_PACKAGE", "package")
                        put("TERMUX_PREFIX", prefixDir.absolutePath)
                    }
                    redirectErrorStream(true)
                }.start()
            } catch (ex: Exception) {
                Log.e(TAG, "Fatal: both local sh and system sh failed to execute script", ex)
            }
        }

        try {
            process?.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Set file permissions from Unix mode
     */
    private fun setFilePermissions(file: File, mode: Int) {
        // owner permissions
        file.setReadable(mode and 0x100 != 0, true) // S_IRUSR
        file.setWritable(mode and 0x080 != 0, true) // S_IWUSR
        file.setExecutable(mode and 0x040 != 0, true) // S_IXUSR

        // Try to set using Runtime.exec for more precise control
        try {
            val octalMode = String.format("%03o", mode and 0x1FF)
            Runtime.getRuntime().exec(arrayOf("chmod", octalMode, file.absolutePath))
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Strip Termux-style prefix from deb data paths.
     * Deb files contain paths like "data/data/com.mermes/files/usr/bin/bash".
     * We need to strip "data/data/<pkg>/files/usr/" to get the relative path "bin/bash".
     */
    private fun stripTermuxPrefix(path: String): String {
        // Match "data/data/<anything>/files/usr/" pattern
        val prefix = "data/data/"
        val idx = path.indexOf(prefix)
        if (idx < 0) return path

        val afterPrefix = path.substring(idx + prefix.length)
        val filesIdx = afterPrefix.indexOf("/files/usr/")
        if (filesIdx < 0) return path

        return afterPrefix.substring(filesIdx + "/files/usr/".length)
    }

    /**
     * Get decompressed input stream adaptively based on the file content headers.
     */
    private fun getDecompressedStream(data: ByteArray): java.io.InputStream {
        val bis = BufferedInputStream(ByteArrayInputStream(data))
        return try {
            CompressorStreamFactory().createCompressorInputStream(bis)
        } catch (e: Exception) {
            // Fallback for uncompressed tar or unrecognized format
            ByteArrayInputStream(data)
        }
    }
}

/**
 * Deb parse exception
 */
class DebParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
