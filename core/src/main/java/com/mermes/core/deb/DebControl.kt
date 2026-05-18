package com.mermes.core.deb

/**
 * Deb package information
 */
data class DebPackage(
    val control: DebControl,
    val controlData: ByteArray,
    val dataData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DebPackage
        return control == other.control
    }

    override fun hashCode(): Int {
        return control.hashCode()
    }
}

/**
 * Deb package control information
 */
data class DebControl(
    val packageName: String,
    val version: String,
    val depends: List<String>, // Dependency list
    val preDepends: List<String>, // Pre-dependency list
    val provides: List<String>, // Virtual packages provided
    val description: String
) {
    companion object {
        /**
         * Parse control file content
         */
        fun parse(content: String): DebControl {
            val fields = mutableMapOf<String, String>()
            var currentKey: String? = null
            var currentValue = StringBuilder()

            content.lines().forEach { line ->
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // Continuation line
                    currentValue.appendLine(line.trim())
                } else {
                    // Save previous field
                    if (currentKey != null) {
                        fields[currentKey!!] = currentValue.toString().trim()
                    }
                    // Parse new field
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        currentKey = line.substring(0, colonIndex).trim()
                        currentValue = StringBuilder(line.substring(colonIndex + 1).trim())
                    }
                }
            }
            // Save last field
            if (currentKey != null) {
                fields[currentKey!!] = currentValue.toString().trim()
            }

            return DebControl(
                packageName = fields["Package"] ?: "",
                version = fields["Version"] ?: "",
                depends = parseDependencyList(fields["Depends"]),
                preDepends = parseDependencyList(fields["Pre-Depends"]),
                provides = parseProvides(fields["Provides"]),
                description = fields["Description"] ?: ""
            )
        }

        /**
         * Parse dependency list (e.g., "libc (>= 2.28), libgcc")
         */
        private fun parseDependencyList(depends: String?): List<String> {
            if (depends.isNullOrBlank()) return emptyList()

            return depends.split(",").map { dep ->
                // Extract package name, ignore version constraints
                dep.trim().split("\\s+".toRegex()).first().trim()
            }.filter { it.isNotEmpty() }
        }

        /**
         * Parse provides list
         */
        private fun parseProvides(provides: String?): List<String> {
            if (provides.isNullOrBlank()) return emptyList()

            return provides.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
