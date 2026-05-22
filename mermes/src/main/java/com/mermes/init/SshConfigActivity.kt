package com.mermes.init

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mermes.MainActivity
import com.mermes.R
import com.mermes.common.log.MermesLog as Log
import com.mermes.connection.AuthType
import com.mermes.connection.ConnectionManager
import com.mermes.connection.SshConfig
import com.mermes.connection.SshConfigManager
import com.mermes.connection.SshConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SshConfigActivity : AppCompatActivity() {

    private var selectedAuthType: AuthType = AuthType.PASSWORD
    private var editingConfigId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ssh_config)

        editingConfigId = intent.getStringExtra(EXTRA_CONFIG_ID)

        findViewById<MaterialButton>(R.id.btnAuthPassword).setOnClickListener {
            selectedAuthType = AuthType.PASSWORD
            updateAuthTypeUI()
        }

        findViewById<MaterialButton>(R.id.btnAuthKey).setOnClickListener {
            selectedAuthType = AuthType.KEY
            updateAuthTypeUI()
        }

        findViewById<MaterialButton>(R.id.btnTestConnection).setOnClickListener {
            testConnection()
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            saveAndConnect()
        }

        findViewById<MaterialButton>(R.id.btnBrowseKey).setOnClickListener {
            // TODO: Open file picker for private key
            Log.i("SshConfig", "Browse key file clicked")
        }

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        if (editingConfigId != null) {
            loadExistingConfig()
        }

        updateAuthTypeUI()
    }

    private fun updateAuthTypeUI() {
        val passwordLayout = findViewById<android.widget.LinearLayout>(R.id.layoutPassword)
        val keyLayout = findViewById<android.widget.LinearLayout>(R.id.layoutKeyFile)
        val btnPassword = findViewById<MaterialButton>(R.id.btnAuthPassword)
        val btnKey = findViewById<MaterialButton>(R.id.btnAuthKey)

        when (selectedAuthType) {
            AuthType.PASSWORD -> {
                passwordLayout.visibility = android.view.View.VISIBLE
                keyLayout.visibility = android.view.View.GONE
                btnPassword.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF238636.toInt())
                btnKey.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF21262D.toInt())
            }
            AuthType.KEY -> {
                passwordLayout.visibility = android.view.View.GONE
                keyLayout.visibility = android.view.View.VISIBLE
                btnPassword.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF21262D.toInt())
                btnKey.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF238636.toInt())
            }
        }
    }

    private fun loadExistingConfig() {
        lifecycleScope.launch {
            val config = com.mermes.connection.SshConfigManagerImpl.getInstance().getConfigById(this@SshConfigActivity, editingConfigId!!)
            if (config != null) {
                withContext(Dispatchers.Main) {
                    findViewById<TextInputEditText>(R.id.editConfigName).setText(config.name)
                    findViewById<TextInputEditText>(R.id.editHost).setText(config.host)
                    findViewById<TextInputEditText>(R.id.editPort).setText(config.port.toString())
                    findViewById<TextInputEditText>(R.id.editUsername).setText(config.username)
                    selectedAuthType = config.authType
                    updateAuthTypeUI()

                    when (config.authType) {
                        AuthType.PASSWORD -> {
                            findViewById<TextInputEditText>(R.id.editPassword).setText(config.password)
                        }
                        AuthType.KEY -> {
                            findViewById<TextInputEditText>(R.id.editKeyPath).setText(config.privateKeyPath)
                            findViewById<TextInputEditText>(R.id.editPassphrase).setText(config.passphrase)
                        }
                    }
                }
            }
        }
    }

    private fun testConnection() {
        val config = buildConfig() ?: return

        findViewById<MaterialButton>(R.id.btnTestConnection).isEnabled = false
        findViewById<MaterialButton>(R.id.btnTestConnection).text = getString(R.string.testing_connection)

        lifecycleScope.launch {
            val state = com.mermes.connection.SshConfigManagerImpl.getInstance().testConnection(config)
            withContext(Dispatchers.Main) {
                findViewById<MaterialButton>(R.id.btnTestConnection).isEnabled = true
                findViewById<MaterialButton>(R.id.btnTestConnection).text = getString(R.string.test_connection)

                when (state) {
                    is SshConnectionState.Connected -> {
                        Toast.makeText(this@SshConfigActivity, R.string.connection_success, Toast.LENGTH_SHORT).show()
                    }
                    is SshConnectionState.Error -> {
                        Toast.makeText(this@SshConfigActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun saveAndConnect() {
        val config = buildConfig() ?: return

        lifecycleScope.launch {
            val saved = com.mermes.connection.SshConfigManagerImpl.getInstance().saveConfig(this@SshConfigActivity, config)
            if (saved) {
                ConnectionManager.setSshMode(config)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SshConfigActivity, R.string.config_saved, Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SshConfigActivity, MainActivity::class.java))
                    finish()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SshConfigActivity, R.string.config_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildConfig(): SshConfig? {
        val name = findViewById<TextInputEditText>(R.id.editConfigName).text.toString().trim()
        val host = findViewById<TextInputEditText>(R.id.editHost).text.toString().trim()
        val portStr = findViewById<TextInputEditText>(R.id.editPort).text.toString().trim()
        val username = findViewById<TextInputEditText>(R.id.editUsername).text.toString().trim()

        if (name.isEmpty() || host.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, R.string.fill_required_fields, Toast.LENGTH_SHORT).show()
            return null
        }

        val port = portStr.toIntOrNull() ?: 22

        val password = if (selectedAuthType == AuthType.PASSWORD) {
            findViewById<TextInputEditText>(R.id.editPassword).text.toString()
        } else null

        val keyPath = if (selectedAuthType == AuthType.KEY) {
            findViewById<TextInputEditText>(R.id.editKeyPath).text.toString().trim()
        } else null

        val passphrase = if (selectedAuthType == AuthType.KEY) {
            findViewById<TextInputEditText>(R.id.editPassphrase).text.toString()
        } else null

        return SshConfig(
            id = editingConfigId ?: com.mermes.connection.SshConfigManagerImpl.generateId(),
            name = name,
            host = host,
            port = port,
            username = username,
            authType = selectedAuthType,
            password = password,
            privateKeyPath = keyPath,
            passphrase = passphrase
        )
    }

    companion object {
        const val EXTRA_CONFIG_ID = "config_id"
    }
}
