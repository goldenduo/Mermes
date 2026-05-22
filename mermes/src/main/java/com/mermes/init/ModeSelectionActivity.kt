package com.mermes.init

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.mermes.MainActivity
import com.mermes.R
import com.mermes.common.log.MermesLog as Log
import com.mermes.connection.AuthType
import com.mermes.connection.ConnectionManager
import com.mermes.connection.SshConfig
import com.mermes.connection.SshConfigManager
import kotlinx.coroutines.launch

class ModeSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        findViewById<CardView>(R.id.cardLocal).setOnClickListener {
            Log.i("ModeSelection", "User selected LOCAL mode")
            ConnectionManager.setLocalMode()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<CardView>(R.id.cardSsh).setOnClickListener {
            Log.i("ModeSelection", "User selected SSH mode")
            startActivity(Intent(this, SshConfigActivity::class.java))
        }

        findViewById<CardView>(R.id.cardRemote).setOnClickListener {
            Log.i("ModeSelection", "User clicked Remote Gateway mode")
            android.widget.Toast.makeText(this, R.string.mode_remote_gateway_toast, android.widget.Toast.LENGTH_LONG).show()
        }

        loadSavedConfigs()
    }

    private fun loadSavedConfigs() {
        lifecycleScope.launch {
            val configs = com.mermes.connection.SshConfigManagerImpl.getInstance().getAllConfigs(this@ModeSelectionActivity)
            if (configs.isNotEmpty()) {
                findViewById<TextView>(R.id.textSavedConfigs).visibility = View.VISIBLE
                val container = findViewById<LinearLayout>(R.id.savedConfigsContainer)
                container.removeAllViews()

                for (config in configs) {
                    val card = createConfigCard(config)
                    container.addView(card)
                }
            }
        }
    }

    private fun createConfigCard(config: SshConfig): View {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dp
            }
            setCardBackgroundColor(0xFF161B22.toInt())
            radius = 12f.dp
            cardElevation = 4f.dp
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val icon = TextView(this).apply {
            text = "🔗"
            textSize = 18f
            setPadding(0, 0, 12.dp, 0)
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val name = TextView(this).apply {
            text = config.name
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }

        val details = TextView(this).apply {
            text = "${config.username}@${config.host}:${config.port}"
            setTextColor(0xFF8B949E.toInt())
            textSize = 12f
        }

        val arrow = TextView(this).apply {
            text = "→"
            setTextColor(0xFF58A6FF.toInt())
            textSize = 18f
        }

        info.addView(name)
        info.addView(details)
        layout.addView(icon)
        layout.addView(info)
        layout.addView(arrow)
        card.addView(layout)

        card.setOnClickListener {
            Log.i("ModeSelection", "Connecting to saved SSH config: ${config.name}")
            ConnectionManager.setSshMode(config)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        return card
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private val Float.dp: Float
        get() = this * resources.displayMetrics.density
}
