package com.mermes.core.terminal

/**
 * JNI Native methods for terminal/PTY operations
 */
internal object NativeTerminalLib {

    /**
     * Load native library
     */
    fun load() {
        System.loadLibrary("mermes-terminal")
    }

    /**
     * Create a child process with PTY
     *
     * @param executable Path to executable
     * @param args Arguments array (args[0] is process name)
     * @param cwd Working directory
     * @param environment Environment variables array ["KEY=VALUE", ...]
     * @param masterFd Output parameter for master PTY fd
     * @return Child process PID
     */
    external fun createSubprocess(
        executable: String,
        args: Array<String>,
        cwd: String,
        environment: Array<String>,
        masterFd: IntArray
    ): Int

    /**
     * Wait for child process to exit
     *
     * @param pid Child process PID
     * @return Exit code
     */
    external fun waitFor(pid: Int): Int

    /**
     * Send signal to child process
     *
     * @param pid Child process PID
     * @param signal Signal number (e.g., 2 for SIGINT, 15 for SIGTERM)
     */
    external fun sendSignal(pid: Int, signal: Int)

    /**
     * Set PTY window size
     *
     * @param masterFd Master PTY fd
     * @param rows Number of rows
     * @param cols Number of columns
     */
    external fun setPtyWindowSize(masterFd: Int, rows: Int, cols: Int)

    /**
     * Close file descriptor
     *
     * @param masterFd File descriptor to close
     */
    external fun closeFd(masterFd: Int)

    /**
     * Read from file descriptor
     *
     * @param fd File descriptor
     * @param buffer Buffer to read into
     * @return Number of bytes read, or -1 on error
     */
    external fun readFromFd(fd: Int, buffer: ByteArray): Int

    /**
     * Write to file descriptor
     *
     * @param fd File descriptor
     * @param data Data to write
     * @return Number of bytes written, or -1 on error
     */
    external fun writeToFd(fd: Int, data: ByteArray): Int

    init {
        load()
    }
}
