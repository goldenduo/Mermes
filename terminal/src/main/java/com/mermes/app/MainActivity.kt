package com.mermes.app

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.mermes.core.terminal.TerminalFragment

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FAILSAFE = "failsafe"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            val failsafe = intent.getBooleanExtra(EXTRA_FAILSAFE, false)
            // 直接使用 core 模块提供的 TerminalFragment，不在 terminal 模块重复实现
            val fragment = TerminalFragment.newInstance(failsafe)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                        as? TerminalFragment
                if (fragment?.onBackPressed() != true) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
