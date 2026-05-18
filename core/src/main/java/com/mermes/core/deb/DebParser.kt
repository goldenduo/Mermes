package com.mermes.core.deb

import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Deb file parser
 */
internal object DebParser {

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
                    controlData = arInput.readBytes(entry.size.toInt())
                }
                entry.name.startsWith("data.tar") -> {
                    dataData = arInput.readBytes(entry.size.toInt())
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
        val tarInput = TarArchiveInputStream(XZCompressorInputStream(ByteArrayInputStream(controlData)))

        var entry: TarArchiveEntry? = tarInput.nextTarEntry
        while (entry != null) {
            if (entry.name == "./control" || entry.name == "control") {
                val content = tarInput.readBytes(entry.size.toInt()).toString(Charsets.UTF_8)
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
        val tarInput = TarArchiveInputStream(XZCompressorInputStream(ByteArrayInputStream(dataData)))

        var entry: TarArchiveEntry? = tarInput.nextTarEntry
        while (entry != null) {
            val name = entry.name.removePrefix("./")

            if (name.isNotEmpty()) {
                val targetFile = File(targetDir, name)

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    // Ensure parent directory exists
                    targetFile.parentFile?.mkdirs()

                    // Extract file
                    FileOutputStream(targetFile).use { out ->
                        tarInput.copyTo(out)
                    }

                    // Set permissions
                    try {
                        val mode = entry.mode
                        if (mode > 0) {
                            // Convert Unix permissions to Java File permissions
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
        val tarInput = TarArchiveInputStream(XZCompressorInputStream(ByteArrayInputStream(controlData)))

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
                val scriptContent = tarInput.readBytes(entry.size.toInt()).toString(Charsets.UTF_8)
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
        try {
            val process = ProcessBuilder(
                "${prefixDir.absolutePath}/bin/sh",
                "-c",
                script
            )
                .environment().apply {
                    put("DPKG_MAINTSCRIPT_NAME", "postinst")
                    put("DPKG_MAINTSCRIPT_PACKAGE", "package")
                }
                .redirectErrorStream(true)
                .start()

            process.waitFor()
        } catch (e: Exception) {
            // Log but don't fail on script errors
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
}

/**
 * Deb parse exception
 */
class DebParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
