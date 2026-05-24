package com.mermes.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.KeyEvent
import android.widget.Button
import android.widget.ToggleButton
import com.mermes.core.terminal.TerminalManager
import com.mermes.core.terminal.view.SpecialButtonState
import com.mermes.core.terminal.view.TerminalView
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class TerminalFragment : Fragment() {

    companion object {
        private const val ARG_FAILSAFE = "failsafe"

        fun newInstance(failsafe: Boolean = false): TerminalFragment {
            return TerminalFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_FAILSAFE, failsafe)
                }
            }
        }
    }

    private var terminalView: TerminalView? = null
    private var isFailsafe = false

    // Modifier key states (three-state: active / locked)
    private val ctrlState = SpecialButtonState()
    private val altState = SpecialButtonState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        terminalView = view.findViewById(R.id.terminalView)
        isFailsafe = arguments?.getBoolean(ARG_FAILSAFE, false) ?: false

        terminalView?.onTextSelected = { text ->
            val ctx = context
            if (ctx != null) {
                Toast.makeText(ctx, getString(R.string.text_copied), Toast.LENGTH_SHORT).show()
            }
        }

        setupShortcutKeys(view)
        startSession()
    }

    private fun setupShortcutKeys(view: View) {
        val tv = terminalView ?: return

        // --- Modifier keys (Ctrl/Alt) with SpecialButtonState ---

        val btnCtrl = view.findViewById<ToggleButton>(R.id.btnCtrl)
        val btnAlt = view.findViewById<ToggleButton>(R.id.btnAlt)

        ctrlState.isCreated = true
        altState.isCreated = true

        ctrlState.onStateChanged = { active, locked ->
            btnCtrl.setOnCheckedChangeListener(null)
            btnCtrl.isChecked = active || locked
            tv.isCtrlToggled = active || locked
            btnCtrl.setOnCheckedChangeListener { _, _ -> ctrlState.toggle() }
        }

        altState.onStateChanged = { active, locked ->
            btnAlt.setOnCheckedChangeListener(null)
            btnAlt.isChecked = active || locked
            tv.isAltToggled = active || locked
            btnAlt.setOnCheckedChangeListener { _, _ -> altState.toggle() }
        }

        btnCtrl.setOnCheckedChangeListener { _, _ -> ctrlState.toggle() }
        btnAlt.setOnCheckedChangeListener { _, _ -> altState.toggle() }

        // Long-press to lock modifier
        btnCtrl.setOnLongClickListener { ctrlState.toggleLock(); true }
        btnAlt.setOnLongClickListener { altState.toggleLock(); true }

        // Sync modifier state from TerminalView (e.g., after hardware key press)
        tv.onModifierStatusChanged = { ctrl, alt ->
            if (!ctrlState.isLocked) {
                btnCtrl.setOnCheckedChangeListener(null)
                btnCtrl.isChecked = ctrl
                btnCtrl.setOnCheckedChangeListener { _, _ -> ctrlState.toggle() }
            }
            if (!altState.isLocked) {
                btnAlt.setOnCheckedChangeListener(null)
                btnAlt.isChecked = alt
                btnAlt.setOnCheckedChangeListener { _, _ -> altState.toggle() }
            }
        }

        // --- Repetitive keys (use ScheduledExecutorService for precise timing) ---

        view.findViewById<Button>(R.id.btnEsc).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_ESCAPE)
        }

        view.findViewById<Button>(R.id.btnTab).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_TAB)
        }

        view.findViewById<Button>(R.id.btnMinus).setRepeatAction {
            tv.sendText("-")
        }

        view.findViewById<Button>(R.id.btnSlash).setRepeatAction {
            tv.sendText("/")
        }

        view.findViewById<Button>(R.id.btnPipe).setRepeatAction {
            tv.sendText("|")
        }

        view.findViewById<Button>(R.id.btnHome).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_MOVE_HOME)
        }

        view.findViewById<Button>(R.id.btnEnd).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_MOVE_END)
        }

        view.findViewById<Button>(R.id.btnPgUp).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_PAGE_UP)
        }

        view.findViewById<Button>(R.id.btnPgDn).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_PAGE_DOWN)
        }

        view.findViewById<Button>(R.id.btnUp).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_DPAD_UP)
        }

        view.findViewById<Button>(R.id.btnDown).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_DPAD_DOWN)
        }

        view.findViewById<Button>(R.id.btnLeft).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_DPAD_LEFT)
        }

        view.findViewById<Button>(R.id.btnRight).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        }

        view.findViewById<Button>(R.id.btnEnter).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_ENTER)
        }

        view.findViewById<Button>(R.id.btnSpace).setRepeatAction {
            tv.sendText(" ")
        }

        // --- Non-repetitive buttons ---

        view.findViewById<Button>(R.id.btnPaste).setOnClickListener {
            tv.pasteFromClipboard()
        }

        view.findViewById<Button>(R.id.btnKeyboard).setOnClickListener {
            tv.toggleKeyboard()
        }
    }

    /** Helper: attach KeyRepeatTouchListener to a Button */
    private fun Button.setRepeatAction(action: () -> Unit) {
        setOnTouchListener(KeyRepeatTouchListener(action))
    }

    private fun startSession() {
        val tv = terminalView ?: return
        val ctx = context ?: return

        if (isFailsafe) {
            Toast.makeText(ctx, getString(R.string.failsafe_notice), Toast.LENGTH_LONG).show()
            tv.startFailsafeSession()
        } else {
            tv.startShellSession()
        }
        tv.showKeyboard()
    }

    fun onBackPressed(): Boolean {
        val session = terminalView?.getSession() ?: return false
        TerminalManager.writeToSession(session, "".toByteArray(Charsets.UTF_8))
        return true
    }

    override fun onDestroyView() {
        terminalView?.detachSession()
        terminalView = null
        super.onDestroyView()
    }

    /**
     * TouchListener for repetitive keys (arrows, enter, etc.)
     * Uses ScheduledExecutorService for precise, consistent repeat timing.
     * Press → immediate action → 400ms delay → repeat every 80ms.
     */
    private class KeyRepeatTouchListener(
        private val action: () -> Unit
    ) : View.OnTouchListener {
        private var executor: ScheduledExecutorService? = null
        private var future: ScheduledFuture<*>? = null
        private var longPressCount = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    longPressCount = 0
                    executor = Executors.newSingleThreadScheduledExecutor()
                    future = executor?.scheduleWithFixedDelay({
                        longPressCount++
                        action()
                    }, LONG_PRESS_TIMEOUT_MS, REPEAT_INTERVAL_MS, TimeUnit.MILLISECONDS)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeat()
                    return true
                }
            }
            return false
        }

        private fun stopRepeat() {
            future?.cancel(false)
            executor?.shutdown()
            executor = null
            future = null
        }

        companion object {
            private const val LONG_PRESS_TIMEOUT_MS = 400L
            private const val REPEAT_INTERVAL_MS = 80L
        }
    }
}
