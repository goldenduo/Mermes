package com.mermes.common.log

import android.util.Log

/**
 * Mermes unified logging system.
 * Manages log level filtering and provides desensitization under release mode.
 */
object MermesLog {

    enum class LogLevel(val value: Int) {
        VERBOSE(2),
        DEBUG(3),
        INFO(4),
        WARN(5),
        ERROR(6),
        NONE(7)
    }

    private var currentLevel = LogLevel.DEBUG
    private var isRelease = false

    /**
     * Set global minimum log level
     */
    fun setLogLevel(level: LogLevel) {
        currentLevel = level
    }

    /**
     * Control release desensitization mode
     */
    fun setReleaseMode(release: Boolean) {
        isRelease = release
        if (release) {
            // Default to INFO and above in release mode
            currentLevel = LogLevel.INFO
        }
    }

    fun v(tag: String, msg: String) {
        if (shouldLog(LogLevel.VERBOSE)) {
            Log.v(tag, msg)
        }
    }

    fun d(tag: String, msg: String) {
        if (shouldLog(LogLevel.DEBUG)) {
            Log.d(tag, msg)
        }
    }

    fun i(tag: String, msg: String) {
        if (shouldLog(LogLevel.INFO)) {
            Log.i(tag, desensitize(msg))
        }
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (shouldLog(LogLevel.WARN)) {
            val cleanMsg = desensitize(msg)
            if (tr != null) {
                Log.w(tag, "$cleanMsg${formatThrowable(tr)}")
            } else {
                Log.w(tag, cleanMsg)
            }
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (shouldLog(LogLevel.ERROR)) {
            val cleanMsg = desensitize(msg)
            if (tr != null) {
                Log.e(tag, "$cleanMsg${formatThrowable(tr)}")
            } else {
                Log.e(tag, cleanMsg)
            }
        }
    }

    private fun shouldLog(level: LogLevel): Boolean {
        // Release mode automatically silences VERBOSE and DEBUG logs
        if (isRelease && level.value < LogLevel.INFO.value) {
            return false
        }
        return level.value >= currentLevel.value
    }

    /**
     * Mask sensitive details (paths, IPs, raw data, package configurations) under Release mode
     */
    private fun desensitize(msg: String): String {
        if (!isRelease) return msg

        var clean = msg
        // 1. Mask absolute folder paths (Linux & Android standard paths)
        clean = clean.replace(Regex("/[a-zA-Z0-9_.-]+(/[a-zA-Z0-9_.-]+)+"), "<path>")

        // 2. Mask IP Addresses
        clean = clean.replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "<ip>")

        // 3. Mask URLs/Endpoints
        clean = clean.replace(Regex("https?://[a-zA-Z0-9_.-]+(:[0-9]+)?(/[a-zA-Z0-9_.-]*)*"), "<url>")

        // 4. Hide raw serializations like JSON packets or environment configs
        if ((clean.startsWith("{") && clean.endsWith("}")) || (clean.startsWith("[") && clean.endsWith("]"))) {
            return "<structured_data_masked>"
        }

        return clean
    }

    /**
     * Restrict throwables stack traces in release mode so they don't dump internal source lines
     */
    private fun formatThrowable(tr: Throwable?): String {
        if (tr == null) return ""
        if (!isRelease) {
            return "\n" + Log.getStackTraceString(tr)
        }

        // Under release mode, only expose basic exception summary
        val sb = StringBuilder()
        sb.append("\nException: ").append(tr.javaClass.name).append(": ").append(desensitize(tr.message ?: ""))
        val elements = tr.stackTrace
        if (elements.isNotEmpty()) {
            // Print up to 2 frames, class and method name only (mask source files and line numbers)
            sb.append("\n\tat ").append(elements[0].className).append(".").append(elements[0].methodName).append("(ReleaseFrame)")
            if (elements.size > 1) {
                sb.append("\n\tat ").append(elements[1].className).append(".").append(elements[1].methodName).append("(ReleaseFrame)")
            }
        }
        return sb.toString()
    }
}
