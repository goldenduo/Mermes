package com.mermes.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.mermes.core.terminal.TerminalSession
import com.mermes.core.terminal.TerminalSessionCallback
import com.mermes.core.terminal.view.TerminalView

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

        // Helper: add long-press repeat to a button
        fun Button.setRepeatAction(action: () -> Unit) {
            setOnTouchListener(RepeatTouchListener(action))
        }

        view.findViewById<Button>(R.id.btnEsc).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_ESCAPE)
        }

        val btnCtrl = view.findViewById<ToggleButton>(R.id.btnCtrl)
        val btnAlt = view.findViewById<ToggleButton>(R.id.btnAlt)

        btnCtrl.setOnCheckedChangeListener { _, isChecked ->
            tv.isCtrlToggled = isChecked
        }

        btnAlt.setOnCheckedChangeListener { _, isChecked ->
            tv.isAltToggled = isChecked
        }

        tv.onModifierStatusChanged = { ctrl, alt ->
            btnCtrl.setOnCheckedChangeListener(null)
            btnAlt.setOnCheckedChangeListener(null)
            btnCtrl.isChecked = ctrl
            btnAlt.isChecked = alt
            btnCtrl.setOnCheckedChangeListener { _, isChecked -> tv.isCtrlToggled = isChecked }
            btnAlt.setOnCheckedChangeListener { _, isChecked -> tv.isAltToggled = isChecked }
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

        view.findViewById<Button>(R.id.btnPaste).setOnClickListener {
            tv.pasteFromClipboard()
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

        view.findViewById<Button>(R.id.btnKeyboard).setOnClickListener {
            tv.toggleKeyboard()
        }
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
        // Send ESC to terminal
        val session = terminalView?.getSession() ?: return false
        TerminalManager.writeToSession(session, "\u001B".toByteArray(Charsets.UTF_8))
        return true
    }

    override fun onDestroyView() {
        terminalView?.detachSession()
        terminalView = null
        super.onDestroyView()
    }

    /**
     * TouchListener that fires action on press, then repeats while held.
     */
    private class RepeatTouchListener(
        private val action: () -> Unit
    ) : View.OnTouchListener {
        private val handler = Handler(Looper.getMainLooper())
        private var isRepeating = false

        private val repeatRunnable = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    isRepeating = true
                    handler.postDelayed(repeatRunnable, REPEAT_DELAY_MS)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    isRepeating = false
                    handler.removeCallbacks(repeatRunnable)
                    return true
                }
            }
            return false
        }

        companion object {
            private const val REPEAT_DELAY_MS = 400L   // delay before repeat starts
            private const val REPEAT_INTERVAL_MS = 80L  // repeat interval
        }
    }
}
