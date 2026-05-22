package com.mermes.connection

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mermes.common.log.MermesLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SshConfigManagerImpl : SshConfigManager {
    private val gson = Gson()

    companion object {
        private const val TAG = "SshConfigManagerImpl"
        private const val PREFS_NAME = "ssh_configs"
        private const val KEY_CONFIGS = "configs_json"
        
        @Volatile
        private var instance: SshConfigManagerImpl? = null

        fun getInstance(): SshConfigManagerImpl {
            return instance ?: synchronized(this) {
                instance ?: SshConfigManagerImpl().also { instance = it }
            }
        }
        
        fun generateId(): String = UUID.randomUUID().toString()
    }

    override suspend fun getAllConfigs(context: Context): List<SshConfig> = withContext(Dispatchers.IO) {
        return@withContext try {
            val prefs = getPrefs(context)
            val json = prefs.getString(KEY_CONFIGS, null) ?: return@withContext emptyList()
            val type = object : TypeToken<List<SshConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SSH configs", e)
            emptyList()
        }
    }

    override suspend fun getConfigById(context: Context, id: String): SshConfig? {
        return getAllConfigs(context).find { it.id == id }
    }

    override suspend fun saveConfig(context: Context, config: SshConfig): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val configs = getAllConfigs(context).toMutableList()
            val index = configs.indexOfFirst { it.id == config.id }
            if (index >= 0) {
                configs[index] = config
            } else {
                configs.add(config)
            }
            saveConfigs(context, configs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save SSH config", e)
            false
        }
    }

    override suspend fun deleteConfig(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val configs = getAllConfigs(context).toMutableList()
            configs.removeAll { it.id == id }
            saveConfigs(context, configs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete SSH config", e)
            false
        }
    }

    override suspend fun setDefault(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val configs = getAllConfigs(context).map {
                it.copy(isDefault = it.id == id)
            }
            saveConfigs(context, configs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set default SSH config", e)
            false
        }
    }

    override suspend fun testConnection(config: SshConfig): SshConnectionState = withContext(Dispatchers.IO) {
        // Simulate remote SSH connection check using standard backoff delay
        return@withContext try {
            kotlinx.coroutines.delay(1000)
            SshConnectionState.Connected(session = "simulated_session_for_${config.host}:${config.port}")
        } catch (e: Exception) {
            SshConnectionState.Error("Simulated SSH connection failed", e)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveConfigs(context: Context, configs: List<SshConfig>) {
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_CONFIGS, gson.toJson(configs)).apply()
    }
}
