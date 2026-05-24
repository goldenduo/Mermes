package com.mermes.app.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetUtils {
    /**
     * 将 assets 中的脚本部署到指定的目标文件并设置可执行权限。
     */
    fun deployAssetScript(context: Context, assetName: String, destFile: File): Boolean {
        return try {
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // 设置所有者可读、可写、可执行 (相当于 chmod 700)
            destFile.setReadable(true, true)
            destFile.setWritable(true, true)
            destFile.setExecutable(true, true)
            true
        } catch (e: Exception) {
            com.mermes.common.log.MermesLog.e("AssetUtils", "Failed to deploy asset script $assetName", e)
            false
        }
    }
}
