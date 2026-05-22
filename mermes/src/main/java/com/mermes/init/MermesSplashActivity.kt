package com.mermes.init

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mermes.R
import com.mermes.common.crash.MermesCrashHandler
import com.mermes.common.log.MermesLog as Log
import com.mermes.core.bootstrap.MermesBootstrap
import com.mermes.core.deb.DebInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MermesSplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var errorCard: View
    private lateinit var errorDetailText: TextView
    private lateinit var btnRetry: Button
    private lateinit var btnExit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        // Automatically set logging release mode based on application debuggable flag
        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Log.setReleaseMode(!isDebuggable)
        MermesCrashHandler.init(applicationContext)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        errorCard = findViewById(R.id.errorCard)
        errorDetailText = findViewById(R.id.errorDetailText)
        btnRetry = findViewById(R.id.btnRetry)
        btnExit = findViewById(R.id.btnExit)

        btnExit.setOnClickListener { finish(); System.exit(0) }
        btnRetry.setOnClickListener { startInitialization() }

        startInitialization()
    }

    private fun startInitialization() {
        errorCard.visibility = View.GONE
        progressBar.progress = 0
        progressBar.isIndeterminate = false
        statusText.text = getString(R.string.splash_installing)

        lifecycleScope.launch {
            initializeEnvironment()
        }
    }

    private suspend fun initializeEnvironment() {
        // Step 1: Install Bootstrap (skip if already installed)
        val bootstrapReady = performBootstrapInstallation()
        if (!bootstrapReady) return // Stop if bootstrap failed after retries

        // Step 2: Install Preset DEB packages (skip if already installed)
        val debsReady = performDebsInstallation()
        if (!debsReady) {
            // Note: Deb install errors are warnings, we proceed to Main but log it
            Log.w("MermesSplash", "Deb packages installation had some failed components, entering Main anyway.")
        }

        // Step 3: Done, start ModeSelectionActivity
        withContext(Dispatchers.Main) {
            statusText.text = getString(R.string.splash_done)
            progressBar.progress = 100
            delay(800)
            startActivity(Intent(this@MermesSplashActivity, ModeSelectionActivity::class.java))
            finish()
        }
    }

    private suspend fun performBootstrapInstallation(): Boolean {
        if (MermesBootstrap.isBootstrapInstalled(this)) {
            Log.i("MermesSplash", "Bootstrap is already installed. Skipping.")
            return true
        }

        val maxRetries = 3
        var attempt = 0
        var success = false
        var lastErrorMsg = "Unknown error"

        while (attempt < maxRetries && !success) {
            attempt++
            Log.i("MermesSplash", "Attempting bootstrap installation: $attempt/$maxRetries")
            
            withContext(Dispatchers.Main) {
                if (attempt > 1) {
                    statusText.text = getString(R.string.splash_bootstrap)
                    errorCard.visibility = View.VISIBLE
                    errorDetailText.text = getString(R.string.splash_bootstrap_retry, attempt, maxRetries, lastErrorMsg)
                } else {
                    statusText.text = getString(R.string.splash_bootstrap)
                }
            }

            // Run on IO thread
            val result = withContext(Dispatchers.IO) {
                try {
                    MermesBootstrap.installBootstrap(this@MermesSplashActivity) { progress ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            progressBar.progress = (progress * 50).toInt()
                        }
                    }
                } catch (e: Exception) {
                    com.mermes.core.bootstrap.BootstrapResult(
                        success = false,
                        duration = 0,
                        extractedFiles = 0,
                        createdSymlinks = 0,
                        error = e.message ?: "Extraction exception"
                    )
                }
            }

            if (result.success) {
                success = true
                Log.i("MermesSplash", "Bootstrap installation succeeded on attempt $attempt")
            } else {
                lastErrorMsg = result.error ?: "Unknown extraction failure"
                Log.w("MermesSplash", "Bootstrap installation failed on attempt $attempt: $lastErrorMsg")
                if (attempt < maxRetries) {
                    delay(1500) // Delay before retrying
                }
            }
        }

        if (!success) {
            withContext(Dispatchers.Main) {
                errorCard.visibility = View.VISIBLE
                errorDetailText.text = getString(R.string.splash_bootstrap_failed, lastErrorMsg)
                statusText.text = getString(R.string.splash_bootstrap_failed_title)
            }
            return false
        }

        withContext(Dispatchers.Main) {
            errorCard.visibility = View.GONE
        }
        return true
    }

    private suspend fun performDebsInstallation(): Boolean {
        if (DebInstaller.isAllPresetInstalled(this)) {
            Log.i("MermesSplash", "All preset debs are already installed. Skipping.")
            return true
        }

        val maxRetries = 3
        var attempt = 0
        var success = false
        var lastErrorMsg = "Unknown error"

        while (attempt < maxRetries && !success) {
            attempt++
            Log.i("MermesSplash", "Attempting deb packages installation: $attempt/$maxRetries")

            withContext(Dispatchers.Main) {
                if (attempt > 1) {
                    errorCard.visibility = View.VISIBLE
                    errorDetailText.text = getString(R.string.splash_deb_retry, "Preset Packages", attempt, maxRetries, lastErrorMsg)
                }
            }

            val result = withContext(Dispatchers.IO) {
                try {
                    val list = DebInstaller.installPresetPackages(this@MermesSplashActivity) { name, current, total ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            statusText.text = getString(R.string.splash_deb, name, current, total)
                            progressBar.progress = 50 + (current * 50 / total)
                        }
                    }
                    val failedPackage = list.firstOrNull { !it.success }
                    if (failedPackage != null) {
                        lastErrorMsg = "${failedPackage.packageName}: ${failedPackage.error}"
                        false
                    } else {
                        true
                    }
                } catch (e: Exception) {
                    lastErrorMsg = e.message ?: "Deb extraction error"
                    false
                }
            }

            if (result) {
                success = true
                Log.i("MermesSplash", "All preset debs installed successfully on attempt $attempt")
            } else {
                Log.w("MermesSplash", "Deb packages installation failed on attempt $attempt: $lastErrorMsg")
                if (attempt < maxRetries) {
                    delay(1500)
                }
            }
        }

        withContext(Dispatchers.Main) {
            errorCard.visibility = View.GONE
        }
        return success
    }
}
