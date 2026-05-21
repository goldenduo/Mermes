package com.mermes.common.crash

import android.content.Context
import com.mermes.common.log.MermesLog

/**
 * Global Uncaught Exception Handler that manages custom crash listeners.
 */
object MermesCrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "MermesCrashHandler"

    interface CrashListener {
        fun onCrash(thread: Thread, throwable: Throwable)
    }

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var customListener: CrashListener? = null
    private var isInitialized = false

    /**
     * Initialize and hook the global uncaught exception handler
     */
    fun init(context: Context) {
        if (isInitialized) return
        
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        isInitialized = true
        MermesLog.i(TAG, "MermesCrashHandler initialized successfully.")
    }

    /**
     * Register a custom callback when a crash occurs
     */
    fun registerCrashListener(listener: CrashListener) {
        customListener = listener
        MermesLog.i(TAG, "Custom crash listener registered.")
    }

    /**
     * Unregister the custom crash callback and restore only default handling
     */
    fun unregisterCrashListener() {
        customListener = null
        MermesLog.i(TAG, "Custom crash listener unregistered.")
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        MermesLog.e(TAG, "Uncaught exception intercepted in thread ${thread.name}", throwable)

        try {
            // Invoke custom listener callback
            customListener?.onCrash(thread, throwable)
        } catch (e: Exception) {
            MermesLog.e(TAG, "Error executing custom crash listener", e)
        } finally {
            // Forward crash to system default handler to complete standard crash sequence
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
