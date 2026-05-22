package com.mermes.app.data.model

/**
 * 连接模式枚举
 */
enum class ConnectionMode {
    LOCAL,      // 本地 PTY 模式
    HTTP,       // 远程 HTTP 模式
    SSH         // 远程 SSH 模式
}

/**
 * 认证方式枚举
 */
enum class AuthType {
    PASSWORD,   // 密码认证
    KEY         // 密钥认证
}

/**
 * SSH 连接配置实体
 */
data class SshConfig(
    val id: String,                     // 唯一标识 (UUID)
    val name: String,                   // 配置名称 (用户自定义)
    val host: String,                   // 主机地址
    val port: Int = 22,                 // SSH 端口
    val username: String,               // 登录用户名
    val authType: AuthType,             // 认证方式
    val password: String? = null,       // 密码认证时的密码
    val privateKeyPath: String? = null, // 密钥认证时的私钥路径
    val passphrase: String? = null,     // 私钥密码 (如有)
    val isDefault: Boolean = false,     // 是否为默认连接
    val lastConnectedAt: Long = 0,      // 最后连接时间戳
    val useEncryption: Boolean = true,  // 是否启用强安全加密
    val useTunnel: Boolean = false,     // 是否启用本地端口转发隧道
    val localPort: Int = 11434,         // 本地监听端口
    val tunnelRemoteHost: String = "127.0.0.1", // 远程转发目标主机
    val tunnelRemotePort: Int = 11434   // 远程转发目标端口
)

/**
 * SSH 连接状态
 */
sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    data class Connected(val sessionId: String) : SshConnectionState()
    data class Error(val message: String, val exception: Throwable? = null) : SshConnectionState()
}

/**
 * HTTP 连接配置
 */
data class HttpConfig(
    val serverUrl: String,              // 服务器 URL
    val apiKey: String? = null,         // 可选的 API Key
    val timeout: Long = 30000           // 超时时间 (毫秒)
)

/**
 * 连接配置 (统一包装)
 */
data class ConnectionConfig(
    val mode: ConnectionMode,
    val sshConfig: SshConfig? = null,
    val httpConfig: HttpConfig? = null
)
