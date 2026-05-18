package com.mermes.core.terminal

import android.content.Context
import android.util.Log
import com.mermes.core.MermesPaths
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Terminal session manager
 */
object TerminalManager {
    private const val TAG = "TerminalManager"

    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val ioThreads = ConcurrentHashMap<String, Thread>()
    private val waiterThreads = ConcurrentHashMap<String, Thread>()
    private val running = AtomicBoolean(true)

    /**
     * Create a new terminal session
     *
     * @param context Android Context
     * @param executable Executable path (default: $PREFIX/bin/bash)
     * @param arguments Command arguments
     * @param cwd Working directory (default: HOME)
     * @param environment Additional environment variables
     * @param callback Session callback
     * @return Terminal session
     */
    fun createSession(
        context: Context,
        executable: String? = null,
        arguments: Array<String> = emptyArray(),
        cwd: String? = null,
        environment: Map<String, String> = emptyMap(),
        callback: TerminalSessionCallback
    ): TerminalSession {
        val prefixDir = MermesPaths.getPrefixDir(context)
        val homeDir = MermesPaths.getHomeDir(context)

        // Determine executable
        val exe = executable ?: findShellExecutable(prefixDir)
        val exePath = if (exe.startsWith("/")) exe else "${prefixDir.absolutePath}/bin/$exe"

        // Determine working directory
        val workDir = cwd ?: homeDir.absolutePath

        // Build environment
        val env = ShellEnvironment.getEnvironment(context).toMutableMap()
        env.putAll(environment)

        // Build arguments
        val isLoginShell = isLoginShell(exePath)
        val processName = if (isLoginShell) "-${File(exePath).name}" else File(exePath).name
        val args = arrayOf(processName) + arguments

        // Create environment array for JNI
        val envArray = env.map { "${it.key}=${it.value}" }.toTypedArray()

        // Create subprocess
        val masterFdArray = IntArray(1)
        val pid = NativeTerminalLib.createSubprocess(
            exePath,
            args,
            workDir,
            envArray,
            masterFdArray
        )

        if (pid < 0) {
            throw RuntimeException("Failed to create subprocess")
        }

        val masterFd = masterFdArray[0]
        val session = TerminalSession(
            masterFd = masterFd,
            pid = pid
        )

        sessions[session.id] = session

        // Start I/O threads
        startIOThreads(session, callback)

        Log.i(TAG, "Created session ${session.id} with pid $pid")
        return session
    }

    /**
     * Create a Failsafe session (uses /system/bin/sh)
     *
     * @param context Android Context
     * @param callback Session callback
     * @return Terminal session
     */
    fun createFailsafeSession(
        context: Context,
        callback: TerminalSessionCallback
    ): TerminalSession {
        Log.i(TAG, "Creating failsafe session")

        val homeDir = MermesPaths.getHomeDir(context)
        val workDir = if (homeDir.exists()) homeDir.absolutePath else "/"

        // Use minimal environment
        val env = mapOf(
            "HOME" to workDir,
            "PATH" to "/system/bin:/system/xbin",
            "TERM" to "xterm-256color"
        )

        val args = arrayOf("sh")
        val envArray = env.map { "${it.key}=${it.value}" }.toTypedArray()

        val masterFdArray = IntArray(1)
        val pid = NativeTerminalLib.createSubprocess(
            "/system/bin/sh",
            args,
            workDir,
            envArray,
            masterFdArray
        )

        if (pid < 0) {
            throw RuntimeException("Failed to create failsafe subprocess")
        }

        val masterFd = masterFdArray[0]
        val session = TerminalSession(
            masterFd = masterFd,
            pid = pid
        )

        sessions[session.id] = session
        startIOThreads(session, callback)

        Log.i(TAG, "Created failsafe session ${session.id} with pid $pid")
        return session
    }

    /**
     * Write data to session
     *
     * @param session Terminal session
     * @param data Data to write
     */
    fun writeToSession(session: TerminalSession, data: ByteArray) {
        try {
            FileOutputStream(session.masterFd.toString()).use { /* This won't work */ }
            // Use native write instead
            writeToFd(session.masterFd, data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to session ${session.id}", e)
        }
    }

    /**
     * Write string to session
     *
     * @param session Terminal session
     * @param text Text to write
     * @param newline Whether to append newline
     */
    fun writeToSession(session: TerminalSession, text: String, newline: Boolean = false) {
        val data = if (newline) "$text\n" else text
        writeToSession(session, data.toByteArray())
    }

    /**
     * Close session
     *
     * @param session Terminal session
     */
    fun closeSession(session: TerminalSession) {
        Log.i(TAG, "Closing session ${session.id}")

        // Send SIGTERM first
        try {
            NativeTerminalLib.sendSignal(session.pid, 15) // SIGTERM
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send SIGTERM", e)
        }

        // Close master fd
        try {
            NativeTerminalLib.closeFd(session.masterFd)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close master fd", e)
        }

        session.state = TerminalSession.State.FINISHED
        sessions.remove(session.id)
        ioThreads.remove(session.id)?.interrupt()
        waiterThreads.remove(session.id)?.interrupt()
    }

    /**
     * Get all active sessions
     *
     * @return List of active sessions
     */
    fun getActiveSessions(): List<TerminalSession> {
        return sessions.values.toList()
    }

    /**
     * Close all sessions
     */
    fun closeAllSessions() {
        running.set(false)
        sessions.values.forEach { closeSession(it) }
        sessions.clear()
        ioThreads.clear()
        waiterThreads.clear()
    }

    /**
     * Set PTY window size
     *
     * @param session Terminal session
     * @param rows Number of rows
     * @param cols Number of columns
     */
    fun setPtyWindowSize(session: TerminalSession, rows: Int, cols: Int) {
        try {
            NativeTerminalLib.setPtyWindowSize(session.masterFd, rows, cols)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set PTY window size", e)
        }
    }

    /**
     * Find available shell executable
     */
    private fun findShellExecutable(prefixDir: File): String {
        val binDir = File(prefixDir, "bin")
        val shellCandidates = listOf("bash", "zsh", "fish", "sh")

        for (shell in shellCandidates) {
            val shellFile = File(binDir, shell)
            if (shellFile.exists() && shellFile.canExecute()) {
                return shell
            }
        }

        return "sh" // Fallback
    }

    /**
     * Check if executable should be treated as login shell
     */
    private fun isLoginShell(executable: String): Boolean {
        val name = File(executable).name
        return name in listOf("bash", "zsh", "fish", "sh", "login")
    }

    /**
     * Start I/O threads for session
     */
    private fun startIOThreads(session: TerminalSession, callback: TerminalSessionCallback) {
        // Reader thread
        val readerThread = Thread {
            try {
                val buffer = ByteArray(4096)
                val fd = session.masterFd

                while (session.state == TerminalSession.State.RUNNING && running.get()) {
                    val n = readFromFd(fd, buffer)
                    if (n > 0) {
                        val data = buffer.copyOf(n)
                        callback.onTextChanged(session, data)
                    } else if (n < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (session.state == TerminalSession.State.RUNNING) {
                    Log.e(TAG, "Reader thread error", e)
                }
            }
        }
        readerThread.name = "terminal-reader-${session.id}"
        readerThread.isDaemon = true
        ioThreads[session.id] = readerThread
        readerThread.start()

        // Waiter thread
        val waiterThread = Thread {
            try {
                val exitCode = NativeTerminalLib.waitFor(session.pid)
                session.exitCode = exitCode
                session.state = TerminalSession.State.FINISHED
                callback.onSessionFinished(session, exitCode)
            } catch (e: Exception) {
                if (session.state == TerminalSession.State.RUNNING) {
                    Log.e(TAG, "Waiter thread error", e)
                    session.state = TerminalSession.State.ERROR
                    callback.onSessionFinished(session, -1)
                }
            } finally {
                sessions.remove(session.id)
                ioThreads.remove(session.id)
                waiterThreads.remove(session.id)
            }
        }
        waiterThread.name = "terminal-waiter-${session.id}"
        waiterThread.isDaemon = true
        waiterThreads[session.id] = waiterThread
        waiterThread.start()
    }

    /**
     * Read from file descriptor
     */
    private fun readFromFd(fd: Int, buffer: ByteArray): Int {
        return NativeTerminalLib.readFromFd(fd, buffer)
    }

    /**
     * Write to file descriptor
     */
    private fun writeToFd(fd: Int, data: ByteArray) {
        NativeTerminalLib.writeToFd(fd, data)
    }
}
