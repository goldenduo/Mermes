package com.mermes.connection

data class SshConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val passphrase: String? = null,
    val isDefault: Boolean = false,
    val lastConnectedAt: Long = 0
)

enum class AuthType {
    PASSWORD,
    KEY
}
