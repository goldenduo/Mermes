package com.mermes.common.i18n

import java.util.Locale

/**
 * I18N 翻译器接口
 */
interface I18nTranslator {
    /**
     * 将底层命令或网络的原始错误文本翻译为指定语言的友好描述
     * @param rawError 底层抛出的异常描述或 Shell stderr 原始输出
     * @param locale 目标语言（目前支持 "zh" 和 "en"）
     * @return 翻译后的本地化错误提示
     */
    fun translate(rawError: String, locale: Locale): String
}

/**
 * Mermes I18N 翻译器实现
 */
class MermesI18nTranslator : I18nTranslator {

    companion object {
        // 错误特征 -> 中文翻译
        private val zhErrorMap = mapOf(
            "Permission denied" to "权限不足，请确认文件读写或执行权限。",
            "Connection refused" to "连接被拒绝，请确认目标服务或 SSH 端口已开启。",
            "No route to host" to "路由不可达，请检查设备网络或 VPN 连通状态。",
            "Connection timed out" to "连接超时，可能网络丢包或远程服务器无响应。",
            "Address already in use" to "端口已被占用，请尝试在设置中更换服务映射端口。",
            "No space left on device" to "设备存储空间不足，Termux 解包失败。",
            "dpkg: error processing package" to "Debian 依赖包损坏或版本冲突，正在自动尝试回退。",
            "Invalid private key" to "SSH 私钥文件解析失败，请确认密钥格式正确。",
            "Authentication failed" to "认证失败，请检查用户名和密码/密钥。",
            "Host key verification failed" to "主机密钥验证失败，请确认服务器指纹。",
            "Network is unreachable" to "网络不可达，请检查网络连接。",
            "Connection reset by peer" to "连接被远程服务器重置。",
            "Broken pipe" to "管道断裂，连接可能已断开。",
            "No such file or directory" to "文件或目录不存在。",
            "Input/output error" to "输入/输出错误，设备可能有问题。",
            "Operation not permitted" to "操作不允许，权限不足。",
            "Too many open files" to "打开文件数过多，系统资源不足。",
            "Out of memory" to "内存不足，系统资源紧张。",
            "Segmentation fault" to "段错误，程序异常崩溃。",
            "Killed" to "进程被终止，可能是内存不足。"
        )

        // 错误特征 -> 英文友好翻译
        private val enErrorMap = mapOf(
            "Permission denied" to "Permission denied. Please verify read/write or execution permissions.",
            "Connection refused" to "Connection refused. Ensure target service or SSH port is active.",
            "No route to host" to "No route to host. Check device internet or VPN connectivity.",
            "Connection timed out" to "Connection timed out. Network is unstable or server is unresponsive.",
            "Address already in use" to "Address already in use. Please select a different port in Settings.",
            "No space left on device" to "Disk space exhausted. Termux installation failed.",
            "dpkg: error processing package" to "Dependency package error or collision. Attempting automatic fallback.",
            "Invalid private key" to "Failed to parse SSH private key. Verify the key format.",
            "Authentication failed" to "Authentication failed. Check username and password/key.",
            "Host key verification failed" to "Host key verification failed. Confirm server fingerprint.",
            "Network is unreachable" to "Network is unreachable. Check network connection.",
            "Connection reset by peer" to "Connection reset by remote server.",
            "Broken pipe" to "Broken pipe. Connection may be lost.",
            "No such file or directory" to "File or directory not found.",
            "Input/output error" to "Input/output error. Device may have issues.",
            "Operation not permitted" to "Operation not permitted. Insufficient permissions.",
            "Too many open files" to "Too many open files. System resources exhausted.",
            "Out of memory" to "Out of memory. System resources low.",
            "Segmentation fault" to "Segmentation fault. Program crashed.",
            "Killed" to "Process killed. Possibly out of memory."
        )
    }

    override fun translate(rawError: String, locale: Locale): String {
        val errorMap = when (locale.language) {
            "zh" -> zhErrorMap
            "en" -> enErrorMap
            else -> enErrorMap
        }

        // 尝试精确匹配
        for ((key, value) in errorMap) {
            if (rawError.contains(key, ignoreCase = true)) {
                return value
            }
        }

        // 如果没有匹配，返回原始错误的友好版本
        return when (locale.language) {
            "zh" -> "未知错误: $rawError"
            else -> "Unknown error: $rawError"
        }
    }
}
