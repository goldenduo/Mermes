package com.mermes.core.utils

import android.content.Context
import com.mermes.core.MermesPaths
import com.mermes.core.terminal.ShellEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 代码级命令行执行器，直接运行外部进程，支持输出流式返回和随时中断
 */
class ShellCommandExecutor(private val context: Context) {

    /**
     * 代表一个正在运行的后台命令任务句柄，可用于控制该命令状态
     */
    class CommandHandle internal constructor(private val process: Process) {
        /**
         * 强制中断/中止正在运行的命令进程
         */
        fun interrupt() {
            process.destroy()
        }

        /**
         * 强力杀死命令进程
         */
        fun destroyForcibly() {
            process.destroyForcibly()
        }

        /**
         * 检查进程是否存活
         */
        fun isAlive(): Boolean = process.isAlive
    }

    /**
     * 执行指定的字符串命令，并以流（Flow）的形式实时、持续地返回输出日志行
     * 
     * @param command 完整的字符串命令行，例如 "ls -la" 或 "python3 -m http.server"
     * @param cwd 工作目录（默认为 HOME 目录）
     * @param environment 额外附加的环境变量
     * @param onHandleCreated 进程创建成功后的句柄回调，调用者可通过此句柄在任何时刻调用 interrupt() 中断命令
     * @return 实时文本输出流（合并了 stdout 和 stderr）
     */
    fun execute(
        command: String,
        cwd: String? = null,
        environment: Map<String, String> = emptyMap(),
        onHandleCreated: ((CommandHandle) -> Unit)? = null
    ): Flow<String> = flow {
        val env = ShellEnvironment.getEnvironment(context).toMutableMap()
        env.putAll(environment)

        val pb = ProcessBuilder()
        
        // 查找 shell 解释器，优先使用本地部署的 bash，否则使用系统 sh
        val prefixDir = MermesPaths.getPrefixDir(context)
        val bashFile = File(prefixDir, "bin/bash")
        val shell = if (bashFile.exists() && bashFile.canExecute()) {
            bashFile.absolutePath
        } else {
            "/system/bin/sh"
        }

        pb.command(shell, "-c", command)
        
        // 注入包含 Termux 依赖所需的环境变量
        pb.environment().putAll(env)
        
        // 设置工作目录
        if (cwd != null) {
            pb.directory(File(cwd))
        } else {
            pb.directory(MermesPaths.getHomeDir(context))
        }
        
        // 合并 stdout 和 stderr，以便捕获完整运行输出
        pb.redirectErrorStream(true)

        val process = pb.start()
        onHandleCreated?.invoke(CommandHandle(process))

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        try {
            var line: String? = reader.readLine()
            while (line != null) {
                emit(line)
                line = reader.readLine()
            }
        } finally {
            reader.close()
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)
}
