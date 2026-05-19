package com.mermes.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mermes.core.bootstrap.MermesBootstrap
import com.mermes.core.terminal.TerminalManager
import com.mermes.core.terminal.TerminalSession
import com.mermes.core.terminal.TerminalSessionCallback
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TerminalCommandTest {

    companion object {
        private lateinit var context: android.content.Context

        @BeforeClass
        @JvmStatic
        fun installBootstrap() {
            context = InstrumentationRegistry.getInstrumentation().targetContext
            runBlocking {
                val result = MermesBootstrap.installBootstrap(context)
                require(result.success) { "Bootstrap install failed: ${result.error}" }
            }
        }
    }

    @Test
    fun testRunSimpleCommand() {
        val result = runCommand("echo", "MERMES_TEST_MARKER")
        assertEquals("Exit code should be 0", 0, result.exitCode)
        assertTrue("Output should contain marker", result.output.contains("MERMES_TEST_MARKER"))
    }

    @Test
    fun testRunLsCommand() {
        val prefix = MermesBootstrap.getPrefixDir(context).absolutePath
        val result = runCommand("ls", "$prefix/bin")
        assertEquals("Exit code should be 0", 0, result.exitCode)
        assertTrue("Output should contain bash", result.output.contains("bash"))
    }

    @Test
    fun testExitCode() {
        val result = runCommand("bash", "-c", "exit 42")
        assertEquals("Exit code should be 42", 42, result.exitCode)
    }

    @Test
    fun testFailsafeSession() {
        val latch = CountDownLatch(1)
        val output = StringBuilder()
        var exitCode = -1

        val callback = object : TerminalSessionCallback {
            override fun onTextChanged(session: TerminalSession, data: ByteArray) {
                synchronized(output) {
                    output.append(String(data))
                }
            }
            override fun onSessionFinished(session: TerminalSession, code: Int) {
                exitCode = code
                latch.countDown()
            }
        }

        val session = TerminalManager.createFailsafeSession(context, callback)
        TerminalManager.writeToSession(session, "echo FAILSAFE_OK\n", newline = false)
        TerminalManager.writeToSession(session, "exit\n", newline = false)

        assertTrue("Session should finish within 10s", latch.await(10, TimeUnit.SECONDS))
        val out = synchronized(output) { output.toString() }
        assertTrue("Output should contain FAILSAFE_OK", out.contains("FAILSAFE_OK"))
    }

    private fun runCommand(vararg args: String): CommandResult {
        val latch = CountDownLatch(1)
        val output = StringBuilder()
        var exitCode = -1

        val callback = object : TerminalSessionCallback {
            override fun onTextChanged(session: TerminalSession, data: ByteArray) {
                synchronized(output) {
                    output.append(String(data))
                }
            }
            override fun onSessionFinished(session: TerminalSession, code: Int) {
                exitCode = code
                latch.countDown()
            }
        }

        val executable = args[0]
        val arguments = args.drop(1).toTypedArray()

        val session = TerminalManager.createSession(
            context = context,
            executable = executable,
            arguments = arguments,
            callback = callback
        )

        assertTrue("Session should finish within 10s", latch.await(10, TimeUnit.SECONDS))

        val out = synchronized(output) { output.toString() }
        return CommandResult(out, exitCode)
    }

    private data class CommandResult(val output: String, val exitCode: Int)
}
