package com.mermes.core.terminal

import java.util.UUID

/**
 * Terminal session representation.
 * Matches Termux's session model with name, title, and running state.
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val masterFd: Int,
    val pid: Int
) {
    /**
     * Session state
     */
    enum class State {
        RUNNING,
        FINISHED,
        ERROR
    }

    /**
     * Current state
     */
    var state: State = State.RUNNING
        internal set

    /**
     * Exit code (only valid in FINISHED state)
     */
    var exitCode: Int = 0
        internal set

    /**
     * User-defined session name (e.g. "bash", "server").
     * Displayed in the session tab/list UI.
     */
    var name: String = ""

    /**
     * Dynamic shell title updated by terminal OSC escape sequences (e.g. OSC 0/2).
     */
    var title: String = ""

    /**
     * Each session maintains its own terminal emulator buffer
     */
    var emulator: com.mermes.core.terminal.view.TerminalEmulator? = null

    /**
     * Whether the session process is still running.
     */
    val isRunning: Boolean get() = state == State.RUNNING
}

/**
 * Terminal session callback interface
 */
interface TerminalSessionCallback {
    /**
     * Called when output data is available from the PTY
     *
     * @param session The terminal session
     * @param data Output data bytes
     */
    fun onTextChanged(session: TerminalSession, data: ByteArray)

    /**
     * Called when the session process finishes
     *
     * @param session The terminal session
     * @param exitCode Exit code
     */
    fun onSessionFinished(session: TerminalSession, exitCode: Int)

    /**
     * Called when the shell updates the dynamic window title via OSC escape sequences.
     * Optional - default implementation does nothing.
     *
     * @param session The terminal session
     * @param title New title string
     */
    fun onTitleChanged(session: TerminalSession, title: String) {}
}
