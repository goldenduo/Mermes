package com.mermes.connection

import android.content.Context
import com.mermes.common.log.MermesLog as Log

object ConnectionManager {
    private const val TAG = "ConnectionManager"
    private var currentMode: ConnectionMode = ConnectionMode.LOCAL
    private var currentSshConfig: SshConfig? = null

    fun getCurrentMode(): ConnectionMode = currentMode

    fun setLocalMode() {
        currentMode = ConnectionMode.LOCAL
        currentSshConfig = null
        Log.i(TAG, "Connection mode set to LOCAL")
    }

    fun setSshMode(config: SshConfig) {
        currentMode = ConnectionMode.SSH
        currentSshConfig = config
        Log.i(TAG, "Connection mode set to SSH: ${config.name}@${config.host}")
    }

    fun getCurrentSshConfig(): SshConfig? = currentSshConfig

    fun isConnected(): Boolean {
        return when (currentMode) {
            ConnectionMode.LOCAL -> true
            ConnectionMode.SSH -> currentSshConfig != null
        }
    }
}
