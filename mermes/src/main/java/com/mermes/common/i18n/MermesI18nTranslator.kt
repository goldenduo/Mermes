package com.mermes.common.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class MermesI18nTranslator(private val context: Context) : I18nTranslator {

    companion object {
        @Volatile
        private var instance: MermesI18nTranslator? = null

        fun getInstance(context: Context): MermesI18nTranslator {
            return instance ?: synchronized(this) {
                instance ?: MermesI18nTranslator(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun translate(rawError: String, locale: Locale): String {
        val conf = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = context.createConfigurationContext(conf)
        val localizedResources = localizedContext.resources

        return when {
            rawError.contains("Permission denied", ignoreCase = true) -> {
                localizedResources.getString(com.mermes.R.string.err_permission_denied)
            }
            rawError.contains("Connection refused", ignoreCase = true) -> {
                localizedResources.getString(com.mermes.R.string.err_connection_refused)
            }
            rawError.contains("No route to host", ignoreCase = true) -> {
                localizedResources.getString(com.mermes.R.string.err_no_route_to_host)
            }
            rawError.contains("Connection timed out", ignoreCase = true) || rawError.contains("Timeout", ignoreCase = true) -> {
                localizedResources.getString(com.mermes.R.string.err_connection_timeout)
            }
            rawError.contains("Address already in use", ignoreCase = true) -> {
                localizedResources.getString(com.mermes.R.string.err_address_already_in_use)
            }
            rawError.contains("No space left on device", ignoreCase = true) -> {
                localizedResources.getString(com.mermes.R.string.err_no_space_left)
            }
            rawError.contains("dpkg: error processing package", ignoreCase = true) -> {
                localizedResources.getString(com.mermes.R.string.err_dpkg_error)
            }
            rawError.contains("Invalid private key", ignoreCase = true) -> {
                localizedResources.getString(com.mermes.R.string.err_invalid_private_key)
            }
            else -> {
                if (rawError.isBlank()) {
                    localizedResources.getString(com.mermes.R.string.err_unknown)
                } else {
                    rawError
                }
            }
        }
    }
}
