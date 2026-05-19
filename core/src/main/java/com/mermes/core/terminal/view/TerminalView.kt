package com.mermes.core.terminal.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.mermes.core.terminal.TerminalManager
import com.mermes.core.terminal.TerminalSession
import com.mermes.core.terminal.TerminalSessionCallback

/**
 * Pseudo-terminal Android View.
 * Binds to a TerminalSession and provides interactive terminal UI.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var emulator: TerminalEmulator? = null
    private var renderer: TerminalRenderer? = null
    private var session: TerminalSession? = null

    private val handler = Handler(Looper.getMainLooper())
    private var cursorVisible = true
    private var cursorBlinkRunnable: Runnable? = null
    private val cursorBlinkRate = 500L // ms

    var colorScheme: TerminalColorScheme = TerminalColorScheme.DEFAULT
        set(value) {
            field = value
            renderer?.colorScheme = value
            invalidate()
        }

    var textSizeSp: Float = 14f
        set(value) {
            field = value
            renderer?.setTextSize(value)
            recalculateDimensions()
            invalidate()
        }

    // Terminal dimensions
    private var termColumns = 0
    private var termRows = 0

    private val sessionCallback = object : TerminalSessionCallback {
        override fun onTextChanged(session: TerminalSession, data: ByteArray) {
            emulator?.append(data)
            post { invalidate() }
        }

        override fun onSessionFinished(session: TerminalSession, exitCode: Int) {
            // Session ended
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Attach a terminal session to this view.
     */
    fun attachSession(session: TerminalSession) {
        detachSession()
        this.session = session

        val columns = termColumns.coerceAtLeast(80)
        val rows = termRows.coerceAtLeast(24)

        val emu = TerminalEmulator(columns, rows)
        emulator = emu

        val ren = TerminalRenderer(context, emu, colorScheme)
        ren.setTextSize(textSizeSp)
        renderer = ren

        emu.onUpdate = { post { invalidate() } }

        startCursorBlink()
    }

    /**
     * Create and attach a new session with default shell.
     */
    fun startShellSession() {
        val session = TerminalManager.createSession(
            context = context,
            callback = sessionCallback
        )
        attachSession(session)
    }

    /**
     * Create and attach a failsafe session (/system/bin/sh).
     */
    fun startFailsafeSession() {
        val session = TerminalManager.createFailsafeSession(
            context = context,
            callback = sessionCallback
        )
        attachSession(session)
    }

    /**
     * Detach the current session.
     */
    fun detachSession() {
        stopCursorBlink()
        session?.let { TerminalManager.closeSession(it) }
        session = null
        emulator = null
        renderer = null
    }

    /**
     * Get the currently attached session.
     */
    fun getSession(): TerminalSession? = session

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateDimensions()
    }

    private fun recalculateDimensions() {
        val r = renderer ?: return
        val newCols = (width / r.fontWidth).toInt().coerceAtLeast(1)
        val newRows = (height / r.fontLineSpacing).toInt().coerceAtLeast(1)

        if (newCols != termColumns || newRows != termRows) {
            termColumns = newCols
            termRows = newRows
            emulator?.resize(newCols, newRows)
            session?.let {
                TerminalManager.setPtyWindowSize(it, newRows, newCols)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer?.render(canvas, cursorVisible)
    }

    // --- Keyboard input ---

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val session = this.session ?: return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                sendChar('\r')
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                sendChar(0x7F.toChar())
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                sendChar('\t')
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> sendEscape("[A")
            KeyEvent.KEYCODE_DPAD_DOWN -> sendEscape("[B")
            KeyEvent.KEYCODE_DPAD_RIGHT -> sendEscape("[C")
            KeyEvent.KEYCODE_DPAD_LEFT -> sendEscape("[D")
            else -> {
                val ch = event.unicodeChar
                if (ch != 0) {
                    val c = ch.toChar()
                    if (event.isCtrlPressed && c in 'a'..'z') {
                        sendChar((c - 'a' + 1).toChar())
                    } else {
                        sendChar(c)
                    }
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    private fun sendChar(ch: Char) {
        session?.let {
            TerminalManager.writeToSession(it, ch.toString().toByteArray())
        }
    }

    private fun sendEscape(seq: String) {
        session?.let {
            TerminalManager.writeToSession(it, "$seq".toByteArray())
        }
    }

    // --- Input Method ---

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection(this)
    }

    private class TerminalInputConnection(private val view: TerminalView) :
        BaseInputConnection(view, true) {

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            view.session?.let {
                TerminalManager.writeToSession(it, text.toString().toByteArray())
            }
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            for (i in 0 until beforeLength) {
                view.sendChar(0x7F.toChar())
            }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            view.onKeyDown(event.keyCode, event)
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            if (actionCode == EditorInfo.IME_ACTION_DONE) {
                view.sendChar('\r')
                return true
            }
            return super.performEditorAction(actionCode)
        }
    }

    // --- Cursor blink ---

    private fun startCursorBlink() {
        stopCursorBlink()
        cursorBlinkRunnable = object : Runnable {
            override fun run() {
                cursorVisible = !cursorVisible
                invalidate()
                handler.postDelayed(this, cursorBlinkRate)
            }
        }
        handler.postDelayed(cursorBlinkRunnable!!, cursorBlinkRate)
    }

    private fun stopCursorBlink() {
        cursorBlinkRunnable?.let { handler.removeCallbacks(it) }
        cursorBlinkRunnable = null
        cursorVisible = true
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            startCursorBlink()
        } else {
            stopCursorBlink()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCursorBlink()
    }

    // --- Text selection & clipboard ---

    /**
     * Copy selected text to clipboard (placeholder — selection not yet implemented).
     */
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    /**
     * Paste clipboard content to terminal.
     */
    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context)
            session?.let {
                TerminalManager.writeToSession(it, text.toString().toByteArray())
            }
        }
    }

    /**
     * Show soft keyboard.
     */
    fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, 0)
    }
}
