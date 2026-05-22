package com.mermes.app.data.remote

import com.google.gson.Gson
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.mermes.common.log.MermesLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
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

        // 配置密钥认证
        if (privateKeyPath != null) {
            val keyFile = File(privateKeyPath)
            if (keyFile.exists()) {
                if (passphrase != null) {
                    jsch.addIdentity(keyFile.absolutePath, passphrase)
                } else {
                    jsch.addIdentity(keyFile.absolutePath)
                }
            }
        }

        val newSession = jsch.getSession(username, host, port)

        // 配置密码认证
        if (password != null && privateKeyPath == null) {
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
