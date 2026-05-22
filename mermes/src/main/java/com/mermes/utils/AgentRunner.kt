package com.mermes.utils

import android.content.Context
import com.mermes.common.log.MermesLog as Log
import com.mermes.core.terminal.TerminalManager
import com.mermes.core.terminal.TerminalSession
import com.mermes.core.terminal.TerminalSessionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object AgentRunner {
    private const val TAG = "AgentRunner"

    data class CommandResult(val output: String, val exitCode: Int)

    /**
     * Execute a local command synchronously in the PTY terminal environment
     */
    suspend fun executeLocalCommand(
        context: Context,
        executable: String,
        arguments: Array<String> = emptyArray()
    ): CommandResult = withContext(Dispatchers.IO) {
        val latch = CountDownLatch(1)
        val outputBuffer = StringBuilder()
        var exitCode = -1

        val callback = object : TerminalSessionCallback {
            override fun onTextChanged(session: TerminalSession, data: ByteArray) {
                synchronized(outputBuffer) {
                    outputBuffer.append(String(data))
                }
            }

            override fun onSessionFinished(session: TerminalSession, code: Int) {
                exitCode = code
                latch.countDown()
            }
        }

        try {
            val session = TerminalManager.createSession(
                context = context,
                executable = executable,
                arguments = arguments,
                callback = callback
            )

            // Limit waiting duration to 15 seconds to prevent frozen threads
            val finished = latch.await(15, TimeUnit.SECONDS)
            if (!finished) {
                Log.w(TAG, "Command execution timed out: $executable ${arguments.joinToString(" ")}")
                TerminalManager.closeSession(session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: $executable", e)
            return@withContext CommandResult("", -1)
        }

        val fullOutput = synchronized(outputBuffer) { outputBuffer.toString() }
        CommandResult(fullOutput, exitCode)
    }

    /**
     * Executes a dynamic Python script locally via bash here-doc to prevent interactive stdin blocks
     */
    suspend fun executePythonScript(context: Context, scriptContent: String): String {
        // Construct bash command utilizing a here-document for seamless scripting
        val bashScript = "python3 - << 'EOF'\n$scriptContent\nEOF\n"
        val result = executeLocalCommand(
            context = context,
            executable = "bash",
            arguments = arrayOf("-c", bashScript)
        )
        return result.output
    }
}
