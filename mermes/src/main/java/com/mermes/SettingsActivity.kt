package com.mermes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mermes.common.log.MermesLog as Log

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("MermesSettings", "Settings screen opened.")
    }
}
