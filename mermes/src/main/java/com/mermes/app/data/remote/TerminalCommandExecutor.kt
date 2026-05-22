package com.mermes.app.data.remote

import com.google.gson.Gson
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.mermes.app.data.model.SshTestFailureReason
import com.mermes.app.data.model.SshTestResult
import com.mermes.common.log.MermesLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties

/**
 * 终端命令执行器
 * 负责在本地或远程终端执行命令并解析 JSON 输出
 */
interface TerminalCommandExecutor {
    /**
     * 执行命令并返回原始输出
     */
    suspend fun execute(command: String): String?

    /**
     * 执行命令并注入 stdin 内容
     */
    suspend fun executeWithStdin(command: String, stdin: String): String?

    /**
     * 执行命令并解析 JSON 输出
     */
    suspend fun <T> executeAndParse(command: String, clazz: Class<T>): T?

    /**
     * 执行 Python 脚本注入
     */
    suspend fun executePythonScript(script: String): String?

    /**
     * 关闭连接
     */
    fun close()
}

/**
 * 基于本地 PTY 的命令执行器
 */
class LocalCommandExecutor : TerminalCommandExecutor {
    private val gson = Gson()

    override suspend fun execute(command: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    output.trim()
                } else {
                    MermesLog.e("LocalCommandExecutor", "Command failed: $command, error: $error")
                    null
                }
            } catch (e: Exception) {
                MermesLog.e("LocalCommandExecutor", "Execute failed", e)
                null
            }
        }
    }

    override suspend fun executeWithStdin(command: String, stdin: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(stdin)
                    writer.flush()
                }
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    output.trim()
                } else {
                    null
                }
            } catch (e: Exception) {
                MermesLog.e("LocalCommandExecutor", "Execute with stdin failed", e)
                null
            }
        }
    }

    override suspend fun <T> executeAndParse(command: String, clazz: Class<T>): T? {
        val output = execute(command) ?: return null
        return try {
            gson.fromJson(output, clazz)
        } catch (e: Exception) {
            MermesLog.e("LocalCommandExecutor", "Parse JSON failed", e)
            null
        }
    }

    override suspend fun executePythonScript(script: String): String? {
        return executeWithStdin("python3 -", script)
    }

    override fun close() {
        // 本地执行器无需关闭
    }
}

/**
 * 基于 SSH 的命令执行器
 */
class SshCommandExecutor(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String? = null,
    private val privateKeyPath: String? = null,
    private val passphrase: String? = null,
    private val useTunnel: Boolean = false,
    private val localPort: Int = 11434,
    private val tunnelRemoteHost: String = "127.0.0.1",
    private val tunnelRemotePort: Int = 11434
) : TerminalCommandExecutor {
    private val gson = Gson()
    private var session: Session? = null

    private fun getSession(): Session {
        if (session?.isConnected == true) {
            return session!!
        }

        val jsch = JSch()

        // 配置密钥认证（只在路径非空且文件存在时添加）
        if (!privateKeyPath.isNullOrBlank()) {
            val keyFile = File(privateKeyPath)
            if (keyFile.exists()) {
                if (!passphrase.isNullOrBlank()) {
                    jsch.addIdentity(keyFile.absolutePath, passphrase)
                } else {
                    jsch.addIdentity(keyFile.absolutePath)
                }
            }
        }

        val newSession = jsch.getSession(username, host, port)

        // 配置密码认证（只要密码不为空就设置，JSch 会自动尝试多种认证方式）
        if (!password.isNullOrBlank()) {
            newSession.setPassword(password)
        }

        // 跳过主机密钥验证（生产环境应改为严格验证）
        val config = Properties()
        config["StrictHostKeyChecking"] = "no"
        newSession.setConfig(config)

        // 设置超时
        newSession.setTimeout(30000)

        newSession.connect()

        // 绑定本地端口转发隧道 (SSH Tunneling)
        if (useTunnel) {
            try {
                newSession.delPortForwardingL(localPort)
            } catch (e: Exception) {
                // 忽略
            }
            try {
                newSession.setPortForwardingL(localPort, tunnelRemoteHost, tunnelRemotePort)
                MermesLog.i("SshCommandExecutor", "Local port forwarding established: 127.0.0.1:$localPort -> $tunnelRemoteHost:$tunnelRemotePort")
            } catch (e: Exception) {
                MermesLog.e("SshCommandExecutor", "Failed to establish port forwarding for port $localPort", e)
                throw java.io.IOException("Port $localPort forwarding failed: ${e.message}", e)
            }
        }

        session = newSession
        MermesLog.i("SshCommandExecutor", "SSH session established to $host:$port")
        return newSession
    }

    override suspend fun execute(command: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val session = getSession()
                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                channel.inputStream = null

                val outputStream = ByteArrayOutputStream()
                channel.outputStream = outputStream

                val errorStream = ByteArrayOutputStream()
                channel.setErrStream(errorStream)

                channel.connect(30000)

                // 等待命令完成
                while (!channel.isClosed) {
                    Thread.sleep(100)
                }

                val exitCode = channel.exitStatus
                channel.disconnect()

                if (exitCode == 0) {
                    outputStream.toString("UTF-8").trim()
                } else {
                    val error = errorStream.toString("UTF-8")
                    MermesLog.e("SshCommandExecutor", "Command failed: $command, error: $error")
                    null
                }
            } catch (e: Exception) {
                MermesLog.e("SshCommandExecutor", "Execute failed", e)
                session?.disconnect()
                session = null
                null
            }
        }
    }

    override suspend fun executeWithStdin(command: String, stdin: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val session = getSession()
                val channel = session.openChannel("exec") as ChannelExec

                // 使用 bash -c 并通过 stdin 传递脚本
                channel.setCommand("bash -c 'cat << \"MERMES_EOF\" | $command\n$stdin\nMERMES_EOF'")

                val outputStream = ByteArrayOutputStream()
                channel.outputStream = outputStream

                val errorStream = ByteArrayOutputStream()
                channel.setErrStream(errorStream)

                channel.connect(30000)

                // 等待命令完成
                while (!channel.isClosed) {
                    Thread.sleep(100)
                }

                val exitCode = channel.exitStatus
                channel.disconnect()

                if (exitCode == 0) {
                    outputStream.toString("UTF-8").trim()
                } else {
                    val error = errorStream.toString("UTF-8")
                    MermesLog.e("SshCommandExecutor", "Execute with stdin failed: $error")
                    null
                }
            } catch (e: Exception) {
                MermesLog.e("SshCommandExecutor", "Execute with stdin failed", e)
                null
            }
        }
    }

    override suspend fun <T> executeAndParse(command: String, clazz: Class<T>): T? {
        val output = execute(command) ?: return null
        return try {
            gson.fromJson(output, clazz)
        } catch (e: Exception) {
            MermesLog.e("SshCommandExecutor", "Parse JSON failed", e)
            null
        }
    }

    override suspend fun executePythonScript(script: String): String? {
        return executeWithStdin("python3 -", script)
    }

    /**
     * 测试 SSH 连接并返回详细结果，用于 UI 展示具体失败原因
     */
    suspend fun testConnection(): SshTestResult {
        return withContext(Dispatchers.IO) {
            try {
                val session = getSession()
                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand("echo 'test'")
                channel.inputStream = null

                val outputStream = ByteArrayOutputStream()
                channel.outputStream = outputStream

                channel.connect(10000)

                while (!channel.isClosed) {
                    Thread.sleep(100)
                }

                val exitCode = channel.exitStatus
                channel.disconnect()

                if (exitCode == 0) {
                    SshTestResult.Success(session.toString())
                } else {
                    SshTestResult.Failure(
                        reason = SshTestFailureReason.UNKNOWN,
                        message = "Command execution failed with exit code $exitCode"
                    )
                }
            } catch (e: Exception) {
                val result = classifySshError(e)
                MermesLog.e("SshCommandExecutor", "Test connection failed: ${result.reason}", e)
                session?.disconnect()
                session = null
                result
            }
        }
    }

    private fun classifySshError(e: Exception): SshTestResult.Failure {
        return when {
            // 认证失败
            e is JSchException && e.message?.contains("Auth fail") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.AUTH_FAILED,
                    message = "认证失败",
                    detail = e.message
                )
            e is JSchException && e.message?.contains("USERAUTH fail") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.AUTH_FAILED,
                    message = "认证失败",
                    detail = e.message
                )
            e is JSchException && e.message?.contains("Auth cancel") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.AUTH_FAILED,
                    message = "认证被取消",
                    detail = e.message
                )

            // 密钥解析失败
            e is JSchException && e.message?.contains("invalid privatekey") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.KEY_PARSE_FAILED,
                    message = "私钥格式无效",
                    detail = e.message
                )
            e is JSchException && e.message?.contains("passphrase") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.KEY_PARSE_FAILED,
                    message = "私钥口令错误",
                    detail = e.message
                )
            e is JSchException && e.message?.contains("identity") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.KEY_PARSE_FAILED,
                    message = "密钥加载失败",
                    detail = e.message
                )

            // 主机密钥验证失败
            e is JSchException && e.message?.contains("HostKey") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.HOST_KEY_CHANGED,
                    message = "主机密钥验证失败",
                    detail = e.message
                )
            e is JSchException && e.message?.contains("verify") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.HOST_KEY_CHANGED,
                    message = "主机密钥验证失败",
                    detail = e.message
                )

            // 端口转发失败
            e is JSchException && e.message?.contains("PortForwardingL") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.PORT_FORWARD_FAILED,
                    message = "本地端口转发失败",
                    detail = e.message
                )
            e.message?.contains("forwarding failed") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.PORT_FORWARD_FAILED,
                    message = "端口转发失败",
                    detail = e.message
                )

            // 网络不可达
            e is UnknownHostException ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.NETWORK_UNREACHABLE,
                    message = "主机地址无法解析",
                    detail = e.message
                )
            e is ConnectException ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.NETWORK_UNREACHABLE,
                    message = "连接被拒绝，端口可能未开放",
                    detail = e.message
                )
            e is NoRouteToHostException ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.NETWORK_UNREACHABLE,
                    message = "网络不可达",
                    detail = e.message
                )

            // 连接超时
            e is SocketTimeoutException ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.CONNECTION_TIMEOUT,
                    message = "连接超时",
                    detail = e.message
                )
            e is JSchException && e.message?.contains("timeout") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.CONNECTION_TIMEOUT,
                    message = "连接超时",
                    detail = e.message
                )
            e is JSchException && e.message?.contains("Timeout") == true ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.CONNECTION_TIMEOUT,
                    message = "连接超时",
                    detail = e.message
                )

            // JSch 通用异常，尝试从 message 提取更多信息
            e is JSchException ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.UNKNOWN,
                    message = "SSH 连接异常",
                    detail = e.message
                )

            // 其他未知异常
            else ->
                SshTestResult.Failure(
                    reason = SshTestFailureReason.UNKNOWN,
                    message = "连接失败",
                    detail = e.message ?: e.toString()
                )
        }
    }

    override fun close() {
        try {
            session?.disconnect()
            session = null
            MermesLog.i("SshCommandExecutor", "SSH session closed")
        } catch (e: Exception) {
            MermesLog.e("SshCommandExecutor", "Error closing SSH session", e)
        }
    }
}

/**
 * 基于 HTTP 的命令执行器
 */
class HttpCommandExecutor(
    private val serverUrl: String,
    private val apiKey: String? = null
) : TerminalCommandExecutor {
    private val gson = Gson()
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun execute(command: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(mapOf("command" to command))

                val requestBuilder = okhttp3.Request.Builder()
                    .url("$serverUrl/api/execute")
                    .post(
                        requestBody.toRequestBody("application/json".toMediaType())
                    )

                if (apiKey != null) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    // 解析响应
                    val jsonResponse = gson.fromJson(body, Map::class.java)
                    jsonResponse["output"]?.toString()?.trim()
                } else {
                    MermesLog.e("HttpCommandExecutor", "HTTP request failed: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                MermesLog.e("HttpCommandExecutor", "Execute failed", e)
                null
            }
        }
    }

    override suspend fun executeWithStdin(command: String, stdin: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(mapOf(
                    "command" to command,
                    "stdin" to stdin
                ))

                val requestBuilder = okhttp3.Request.Builder()
                    .url("$serverUrl/api/execute")
                    .post(
                        requestBody.toRequestBody("application/json".toMediaType())
                    )

                if (apiKey != null) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val jsonResponse = gson.fromJson(body, Map::class.java)
                    jsonResponse["output"]?.toString()?.trim()
                } else {
                    null
                }
            } catch (e: Exception) {
                MermesLog.e("HttpCommandExecutor", "Execute with stdin failed", e)
                null
            }
        }
    }

    override suspend fun <T> executeAndParse(command: String, clazz: Class<T>): T? {
        val output = execute(command) ?: return null
        return try {
            gson.fromJson(output, clazz)
        } catch (e: Exception) {
            MermesLog.e("HttpCommandExecutor", "Parse JSON failed", e)
            null
        }
    }

    override suspend fun executePythonScript(script: String): String? {
        return executeWithStdin("python3 -", script)
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
