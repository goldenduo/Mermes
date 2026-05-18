package com.mermes.core.terminal

import java.util.UUID

/**
 * Terminal session representation
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
}

/**
 * Terminal session callback interface
 */
interface TerminalSessionCallback {
    /**
     * Called when output data is available
     *
     * @param session The terminal session
     * @param data Output data
     */
    fun onTextChanged(session: TerminalSession, data: ByteArray)

    /**
     * Called when session finishes
     *
     * @param session The terminal session
     * @param exitCode Exit code
     */
    fun onSessionFinished(session: TerminalSession, exitCode: Int)
}
