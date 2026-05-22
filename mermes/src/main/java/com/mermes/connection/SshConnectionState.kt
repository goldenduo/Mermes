package com.mermes.connection

sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    data class Connected(val session: Any) : SshConnectionState()
    data class Error(val message: String, val exception: Throwable? = null) : SshConnectionState()
}
