package com.mermes.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.mermes.common.log.MermesLog

class MermesApplication : Application() {

    companion object {
        const val CHANNEL_ID_SSH_TUNNEL = "ssh_tunnel_channel"
        const val CHANNEL_ID_BOOTSTRAP = "bootstrap_channel"
        const val CHANNEL_ID_GENERAL = "general_channel"
    }

    override fun onCreate() {
        super.onCreate()
        MermesLog.i("MermesApplication", "Application initializing")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val sshChannel = NotificationChannel(
                CHANNEL_ID_SSH_TUNNEL,
                "SSH Tunnel Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SSH tunnel connection keep-alive service"
            }

            val bootstrapChannel = NotificationChannel(
                CHANNEL_ID_BOOTSTRAP,
                "Bootstrap Installation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Bootstrap environment installation progress"
            }

            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General application notifications"
            }

            manager.createNotificationChannels(listOf(sshChannel, bootstrapChannel, generalChannel))
        }
    }
}
