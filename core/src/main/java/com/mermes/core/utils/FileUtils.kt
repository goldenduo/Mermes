package com.mermes.core.utils

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * File operation utilities
 */
object FileUtils {

    /**
     * Recursively delete directory
     *
     * @param dir Directory to delete
     */
    fun deleteRecursive(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteRecursive(it) }
        }
        dir.delete()
    }

    /**
     * Create directory and parents
     *
     * @param dir Directory to create
     * @param mode Directory permission mode
     */
    fun createDir(dir: File, mode: Int = 493) { // 493 = 0755 in decimal
        if (!dir.exists()) {
            dir.mkdirs()
            try {
                NativeUtils.chmod(dir.absolutePath, mode)
            } catch (e: Exception) {
                // Ignore chmod errors on some systems
            }
        }
    }

    /**
     * Extract zip to target directory
     *
     * @param zipData Zip file byte array
     * @param targetDir Target directory
     * @param filter Filter function (return true to extract)
     * @return Number of extracted files
     */
    fun extractZip(
        zipData: ByteArray,
        targetDir: File,
        filter: ((String) -> Boolean)? = null
    ): Int {
        var count = 0
        ZipInputStream(ByteArrayInputStream(zipData)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name

                if (filter == null || filter(name)) {
                    val targetFile = File(targetDir, name)

                    if (entry.isDirectory) {
                        createDir(targetFile)
                    } else {
                        // Ensure parent directory exists
                        createDir(targetFile.parentFile!!)

                        // Extract file
                        FileOutputStream(targetFile).use { out ->
                            zip.copyTo(out)
                        }
                        count++
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return count
    }

    /**
     * Parse SYMLINKS.txt from zip
     *
     * @param zipData Zip file byte array
     * @return List of (target, linkPath) pairs
     */
    fun parseSymlinksFile(zipData: ByteArray): List<Pair<String, String>> {
        val symlinks = mutableListOf<Pair<String, String>>()

        ZipInputStream(ByteArrayInputStream(zipData)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name == "SYMLINKS.txt") {
                    val content = zip.bufferedReader().readLines()
                    for (line in content) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            val parts = trimmed.split("←")
                            if (parts.size == 2) {
                                symlinks.add(Pair(parts[0], parts[1]))
                            }
                        }
                    }
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return symlinks
    }

    /**
     * Parse shebang line from file
     *
     * @param file File to check
     * @return Interpreter path, or null if no shebang
     */
    fun parseShebang(file: File): String? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            val bytes = ByteArray(256)
            FileInputStream(file).use { fis ->
                val n = fis.read(bytes)
                if (n >= 2 && bytes[0] == '#'.code.toByte() && bytes[1] == '!'.code.toByte()) {
                    val line = String(bytes, 0, n.coerceAtMost(256))
                    val shebangLine = line.substring(2).trim()
                    val parts = shebangLine.split("\\s+".toRegex())
                    parts.firstOrNull()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
