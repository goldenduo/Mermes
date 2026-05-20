package com.mermes.app

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mermes.core.bootstrap.MermesBootstrap
import com.mermes.core.deb.DebInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        // Skip if bootstrap installed and all preset debs installed
        if (MermesBootstrap.isBootstrapInstalled(this) && DebInstaller.isAllPresetInstalled(this)) {
            startMain()
            return
        }

        lifecycleScope.launch {
            initializeEnvironment()
        }
    }

    private suspend fun initializeEnvironment() {
        var bootstrapFailed = false

        // Step 1: Install bootstrap (skip if already installed)
        if (!MermesBootstrap.isBootstrapInstalled(this)) {
            withContext(Dispatchers.IO) {
                updateStatus(getString(R.string.splash_bootstrap))
                updateProgress(0)

                val result = MermesBootstrap.installBootstrap(this@SplashActivity) { progress ->
                    updateProgress((progress * 50).toInt())
                }

                if (!result.success) {
                    bootstrapFailed = true
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SplashActivity,
                            getString(R.string.splash_bootstrap_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        // Step 2: Install preset deb packages (skip if all already installed)
        if (!bootstrapFailed && !DebInstaller.isAllPresetInstalled(this)) {
            withContext(Dispatchers.IO) {
                try {
                    DebInstaller.installPresetPackages(this@SplashActivity) { name, current, total ->
                        updateStatus(getString(R.string.splash_deb, name, current, total))
                        updateProgress(50 + (current * 50 / total))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SplashActivity,
                            "Deb 安装异常: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // Step 3: Done
        updateStatus(getString(R.string.splash_done))
        updateProgress(100)

        startMain(bootstrapFailed)
    }

    private fun startMain(failsafe: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FAILSAFE, failsafe)
        }
        startActivity(intent)
        finish()
    }

    private fun updateStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    private fun updateProgress(progress: Int) {
        runOnUiThread { progressBar.progress = progress }
    }
}
