package com.mermes.core.terminal

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.mermes.core.R
import com.mermes.core.terminal.view.SpecialButtonState
import com.mermes.core.terminal.view.TerminalView
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Terminal Fragment with Termux-compatible multi-session management and
 * extra keys layout matching DEFAULT_IVALUE_EXTRA_KEYS:
 *
 *   Row 1: ESC  /  -  HOME  UP  END  PGUP
 *   Row 2: TAB  CTRL  ALT  LEFT  DOWN  RIGHT  PGDN
 */
class TerminalFragment : Fragment() {

    companion object {
        private const val ARG_FAILSAFE = "failsafe"
        const val MAX_SESSIONS = 8

        fun newInstance(failsafe: Boolean = false): TerminalFragment {
            return TerminalFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_FAILSAFE, failsafe)
                }
            }
        }
    }

    // ── Views ────────────────────────────────────────────────────────────────

    private var terminalView: TerminalView? = null
    private var sessionTabLayout: LinearLayout? = null
    private var sessionTabScroll: View? = null

    // ── State ────────────────────────────────────────────────────────────────

    private var isFailsafe = false

    /** All open sessions in insertion order */
    private val sessions = mutableListOf<TerminalSession>()

    /** Currently visible session */
    private var currentSession: TerminalSession? = null

    // ── Modifier key tri-state (normal → active → locked) ────────────────────

    private val ctrlState = SpecialButtonState()
    private val altState = SpecialButtonState()

    // ── Fragment Lifecycle ────────────────────────────────────────────────────

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
        sessionTabLayout = view.findViewById(R.id.sessionTabLayout)
        sessionTabScroll = view.findViewById(R.id.sessionTabScroll)

        isFailsafe = arguments?.getBoolean(ARG_FAILSAFE, false) ?: false

        terminalView?.onTextSelected = { _ ->
            val ctx = context
            if (ctx != null) {
                Toast.makeText(ctx, getString(R.string.text_copied), Toast.LENGTH_SHORT).show()
            }
        }

        setupExtraKeys(view)
        createInitialSession()
    }

    override fun onDestroyView() {
        terminalView?.detachSession()
        terminalView = null
        sessionTabLayout = null
        sessionTabScroll = null
        super.onDestroyView()
    }

    // ── Session API ───────────────────────────────────────────────────────────

    /**
     * Create a new PTY session and switch to it.
     */
    fun createNewSession() {
        val ctx = context ?: return
        if (sessions.size >= MAX_SESSIONS) {
            Toast.makeText(ctx, getString(R.string.session_max_reached, MAX_SESSIONS), Toast.LENGTH_SHORT).show()
            return
        }

        val session = createSession(ctx)
        sessions.add(session)
        rebuildSessionTabs()
        switchToSession(session)
    }

    /**
     * Switch the visible session displayed in TerminalView.
     */
    fun switchToSession(session: TerminalSession) {
        currentSession = session
        terminalView?.attachSession(session)
        rebuildSessionTabs()
        terminalView?.showKeyboard()
    }

    /**
     * Close the currently active session.
     * If more sessions remain, automatically switch to the nearest one.
     */
    fun closeCurrentSession() {
        val session = currentSession ?: return
        val ctx = context ?: return

        val idx = sessions.indexOf(session)
        sessions.remove(session)
        TerminalManager.closeSession(session)

        when {
            sessions.isEmpty() -> {
                // No more sessions — optionally re-create one or notify host
                currentSession = null
                rebuildSessionTabs()
                // Re-create a default session so the terminal stays usable
                createInitialSession()
            }
            idx < sessions.size -> switchToSession(sessions[idx])
            else -> switchToSession(sessions[sessions.size - 1])
        }
    }

    /**
     * Show a dialog to rename the current session.
     */
    fun renameCurrentSession(newName: String) {
        currentSession?.name = newName
        rebuildSessionTabs()
    }

    fun showRenameDialog() {
        val ctx = context ?: return
        val session = currentSession ?: return
        val editText = android.widget.EditText(ctx).apply {
            setText(session.name)
            hint = getString(R.string.session_rename_hint)
        }
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.session_rename))
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) renameCurrentSession(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Get the currently active session */
    fun getCurrentSession(): TerminalSession? = currentSession

    /** Get a snapshot of all sessions */
    fun getSessions(): List<TerminalSession> = sessions.toList()

    /**
     * Handle back press from the host Activity.
     * Sends ESC to the running session; returns true if consumed.
     */
    fun onBackPressed(): Boolean {
        val session = currentSession ?: return false
        if (!session.isRunning) return false
        terminalView?.sendControlKey(KeyEvent.KEYCODE_ESCAPE)
        return true
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private fun createInitialSession() {
        val ctx = context ?: return
        val session = createSession(ctx)
        sessions.add(session)
        switchToSession(session)
    }

    private fun createSession(ctx: Context): TerminalSession {
        val sessionNumber = sessions.size + 1
        val callback = object : TerminalSessionCallback {
            override fun onTextChanged(session: TerminalSession, data: ByteArray) {
                // 收到底层 PTY 的数据，分发给该会话的专属仿真器
                session.emulator?.append(data)

                // 如果是当前正在显示的会话，调度 UI 线程进行局部重绘
                if (session == currentSession) {
                    activity?.runOnUiThread {
                        terminalView?.invalidate()
                    }
                }
            }

            override fun onSessionFinished(session: TerminalSession, exitCode: Int) {
                activity?.runOnUiThread {
                    // Show exit notice in the terminal (emulate Termux behavior)
                    val notice = getString(R.string.session_finished, exitCode)
                    session.emulator?.append("\r\n$notice\r\n".toByteArray(Charsets.UTF_8))
                    rebuildSessionTabs()
                    if (session == currentSession) {
                        terminalView?.invalidate()
                    }
                }
            }

            override fun onTitleChanged(session: TerminalSession, title: String) {
                activity?.runOnUiThread { rebuildSessionTabs() }
            }
        }

        val session = if (isFailsafe) {
            TerminalManager.createFailsafeSession(ctx, callback).also { s ->
                s.name = getString(R.string.session_default_name)
            }
        } else {
            TerminalManager.createSession(ctx, callback = callback).also { s ->
                s.name = "$sessionNumber: ${getString(R.string.session_default_name)}"
            }
        }

        // 初始化该会话专属的终端仿真器状态，默认先赋予宿主尺寸或 80 * 24 尺寸
        val cols = terminalView?.getColumnCount()?.coerceAtLeast(80) ?: 80
        val rows = terminalView?.getRowCount()?.coerceAtLeast(24) ?: 24
        session.emulator = com.mermes.core.terminal.view.TerminalEmulator(cols, rows).apply {
            onUpdate = {
                if (session == currentSession) {
                    activity?.runOnUiThread {
                        terminalView?.invalidate()
                    }
                }
            }
        }

        return session
    }

    /**
     * Rebuild the session tab bar UI to reflect current sessions and selection.
     * The bar is hidden when there is only one session (like Termux's default behavior
     * — it only shows the drawer for session management).
     */
    private fun rebuildSessionTabs() {
        val tabLayout = sessionTabLayout ?: return
        val tabScroll = sessionTabScroll ?: return
        val ctx = context ?: return

        tabLayout.removeAllViews()

        if (sessions.size <= 1) {
            tabScroll.visibility = View.GONE
            return
        }

        tabScroll.visibility = View.VISIBLE

        sessions.forEachIndexed { idx, session ->
            val tab = TextView(ctx).apply {
                val displayName = session.name.ifEmpty { session.title.ifEmpty { "${idx + 1}" } }
                val suffix = if (!session.isRunning) " ✗" else ""
                text = " $displayName$suffix "
                textSize = 12f
                setPadding(16, 0, 16, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                isSelected = (session == currentSession)

                if (session == currentSession) {
                    setBackgroundColor(0xFF333333.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                } else {
                    setBackgroundColor(0xFF1A1A1A.toInt())
                    setTextColor(0xFF888888.toInt())
                }

                // Single tap: switch
                setOnClickListener { switchToSession(session) }

                // Long tap: rename
                setOnLongClickListener {
                    if (session == currentSession) showRenameDialog()
                    true
                }
            }

            tabLayout.addView(tab, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ))

            // Thin separator
            if (idx < sessions.size - 1) {
                val divider = View(ctx).apply {
                    setBackgroundColor(0xFF444444.toInt())
                }
                tabLayout.addView(divider, LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT))
            }
        }
    }

    // ── Extra Keys Setup (Termux DEFAULT_IVALUE_EXTRA_KEYS) ───────────────────

    private fun setupExtraKeys(view: View) {
        val tv = terminalView ?: return

        // ── CTRL modifier (ToggleButton, tri-state) ──────────────────────────
        val btnCtrl = view.findViewById<ToggleButton>(R.id.btnCtrl)
        ctrlState.isCreated = true
        ctrlState.onStateChanged = { active, locked ->
            btnCtrl.setOnCheckedChangeListener(null)
            btnCtrl.isChecked = active || locked
            // Visual feedback: locked = tinted, active = checked, off = normal
            btnCtrl.setBackgroundColor(
                when {
                    locked -> 0xFF4A6078.toInt()    // blue-ish lock indicator
                    active -> 0xFF3D6B45.toInt()    // green-ish active indicator
                    else   -> 0xFF2D2D2D.toInt()
                }
            )
            tv.isCtrlToggled = active || locked
            btnCtrl.setOnCheckedChangeListener { _, _ -> ctrlState.toggle() }
        }
        btnCtrl.setOnCheckedChangeListener { _, _ -> ctrlState.toggle() }
        btnCtrl.setOnLongClickListener { ctrlState.toggleLock(); true }

        // ── ALT modifier (ToggleButton, tri-state) ───────────────────────────
        val btnAlt = view.findViewById<ToggleButton>(R.id.btnAlt)
        altState.isCreated = true
        altState.onStateChanged = { active, locked ->
            btnAlt.setOnCheckedChangeListener(null)
            btnAlt.isChecked = active || locked
            btnAlt.setBackgroundColor(
                when {
                    locked -> 0xFF4A6078.toInt()
                    active -> 0xFF3D6B45.toInt()
                    else   -> 0xFF2D2D2D.toInt()
                }
            )
            tv.isAltToggled = active || locked
            btnAlt.setOnCheckedChangeListener { _, _ -> altState.toggle() }
        }
        btnAlt.setOnCheckedChangeListener { _, _ -> altState.toggle() }
        btnAlt.setOnLongClickListener { altState.toggleLock(); true }

        // Sync modifier state from hardware key events in TerminalView
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

        // ── Row 1: ESC  /  -  HOME  UP  END  PGUP ─────────────────────────

        // ESC — repeatable
        view.findViewById<Button>(R.id.btnEsc).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_ESCAPE)
        }

        // / — single fire (text input)
        view.findViewById<Button>(R.id.btnSlash).setOnClickListener { tv.sendText("/") }

        // - — single fire; long press sends |
        view.findViewById<Button>(R.id.btnMinus).apply {
            setOnClickListener { tv.sendText("-") }
            setOnLongClickListener { tv.sendText("|"); true }
        }

        // | — popup key (tap to send |)
        view.findViewById<Button>(R.id.btnPipe).setOnClickListener { tv.sendText("|") }

        // HOME — repeatable
        view.findViewById<Button>(R.id.btnHome).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_MOVE_HOME)
        }

        // UP — repeatable
        view.findViewById<Button>(R.id.btnUp).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_DPAD_UP)
        }

        // END — repeatable
        view.findViewById<Button>(R.id.btnEnd).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_MOVE_END)
        }

        // PGUP — repeatable
        view.findViewById<Button>(R.id.btnPgUp).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_PAGE_UP)
        }

        // ── Row 2: TAB  CTRL  ALT  LEFT  DOWN  RIGHT  PGDN ─────────────────

        // TAB — repeatable
        view.findViewById<Button>(R.id.btnTab).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_TAB)
        }

        // LEFT — repeatable
        view.findViewById<Button>(R.id.btnLeft).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_DPAD_LEFT)
        }

        // DOWN — repeatable
        view.findViewById<Button>(R.id.btnDown).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_DPAD_DOWN)
        }

        // RIGHT — repeatable
        view.findViewById<Button>(R.id.btnRight).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        }

        // KEYBOARD — toggle soft keyboard (single fire)
        view.findViewById<Button>(R.id.btnKeyboard).setOnClickListener {
            tv.toggleKeyboard()
        }

        // PGDN — repeatable
        view.findViewById<Button>(R.id.btnPgDn).setRepeatAction {
            tv.sendControlKey(KeyEvent.KEYCODE_PAGE_DOWN)
        }
    }

    /** Attach a KeyRepeatTouchListener to a Button */
    private fun Button.setRepeatAction(action: () -> Unit) {
        setOnTouchListener(KeyRepeatTouchListener(action))
    }

    // ── Key Repeat Touch Listener ─────────────────────────────────────────────

    /**
     * Implements Termux-style key repeat:
     *   Press → immediate → 400ms delay → repeat every 80ms.
     */
    private class KeyRepeatTouchListener(
        private val action: () -> Unit
    ) : View.OnTouchListener {

        private var executor: ScheduledExecutorService? = null
        private var future: ScheduledFuture<*>? = null

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    executor = Executors.newSingleThreadScheduledExecutor()
                    future = executor?.scheduleWithFixedDelay(
                        { action() },
                        LONG_PRESS_TIMEOUT_MS,
                        REPEAT_INTERVAL_MS,
                        TimeUnit.MILLISECONDS
                    )
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    future?.cancel(false)
                    executor?.shutdown()
                    executor = null
                    future = null
                    return true
                }
            }
            return false
        }

        companion object {
            private const val LONG_PRESS_TIMEOUT_MS = 400L
            private const val REPEAT_INTERVAL_MS = 80L
        }
    }
}
