package com.mermes.core.terminal.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
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

    // Scroll state
    private var scrollOffset = 0 // lines scrolled up from bottom
    private var isScrolling = false
    private var scrollStartY = 0f
    private var scrollStartOffset = 0

    // Modifier states for virtual keys
    var isCtrlToggled = false
        set(value) {
            field = value
            onModifierStatusChanged?.invoke(isCtrlToggled, isAltToggled)
        }
    var isAltToggled = false
        set(value) {
            field = value
            onModifierStatusChanged?.invoke(isCtrlToggled, isAltToggled)
        }
    var onModifierStatusChanged: ((ctrl: Boolean, alt: Boolean) -> Unit)? = null
    var onTextSelected: ((String) -> Unit)? = null

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

    // Text selection variables
    private var selectionStartCol = -1
    private var selectionStartRow = -1
    private var selectionEndCol = -1
    private var selectionEndRow = -1
    private var isSelecting = false
    private var lastTouchX: Float? = null
    private var lastTouchY: Float? = null
    private var draggingHandle = 0 // 0: none, 1: start, 2: end
    private var actionMode: android.view.ActionMode? = null

    private val selectionPaint = Paint().apply {
        color = 0x600099FF.toInt() // Semi-transparent blue
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint().apply {
        color = 0xFF0099FF.toInt() // Vibrant blue
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Scrollbar
    private val scrollbarPaint = Paint().apply {
        color = 0x80FFFFFF.toInt() // semi-transparent white
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private var scrollbarAlpha = 0 // 0 = invisible, 255 = fully visible
    private val scrollbarFadeRunnable = Runnable { fadeOutScrollbar() }
    private val scrollbarFadeDelay = 1500L // ms before fade starts
    private val scrollbarWidthDp = 3f
    private val scrollbarMinHeightDp = 24f

    fun startSelectionMode() {
        isSelecting = true
        if (actionMode == null) {
            actionMode = startActionMode(object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                    menu.add(0, 1, 0, android.R.string.copy)
                    return true
                }
                
                override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                    return false
                }
                
                override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                    if (item.itemId == 1) {
                        val text = copySelection()
                        if (text != null) {
                            onTextSelected?.invoke(text)
                        }
                        mode.finish()
                        return true
                    }
                    return false
                }
                
                override fun onDestroyActionMode(mode: android.view.ActionMode) {
                    actionMode = null
                    isSelecting = false
                    invalidate()
                }
            })
        }
    }

    init {
        setOnLongClickListener {
            val lx = lastTouchX
            val ly = lastTouchY
            if (lx != null && ly != null) {
                val r = renderer
                if (r != null) {
                    val (col, row) = r.coordToColRow(lx, ly)
                    selectionStartCol = col
                    selectionStartRow = row
                    selectionEndCol = col
                    selectionEndRow = row
                    startSelectionMode()
                    invalidate()
                    true
                } else false
            } else false
        }
    }

    private val sessionCallback = object : TerminalSessionCallback {
        override fun onTextChanged(session: TerminalSession, data: ByteArray) {
            val emu = emulator
            if (emu != null) {
                emu.append(data)
                // Auto-scroll to bottom when new output arrives
                if (scrollOffset > 0) {
                    scrollOffset = 0
                }
                post { invalidate() }
            }
        }

        override fun onSessionFinished(session: TerminalSession, exitCode: Int) {
            val emu = emulator
            if (emu != null) {
                val msg = "\r\n[Process completed (code $exitCode)]\r\n"
                emu.append(msg.toByteArray(Charsets.UTF_8))
                post { invalidate() }
            }
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Create and attach a new session with default shell.
     */
    fun startShellSession() {
        initEmulator()
        val session = TerminalManager.createSession(
            context = context,
            callback = sessionCallback
        )
        this.session = session
        startCursorBlink()
    }

    /**
     * Create and attach a failsafe session (/system/bin/sh).
     */
    fun startFailsafeSession() {
        initEmulator()
        val session = TerminalManager.createFailsafeSession(
            context = context,
            callback = sessionCallback
        )
        this.session = session
        startCursorBlink()
    }

    private fun initEmulator() {
        detachSession()

        val columns = termColumns.coerceAtLeast(80)
        val rows = termRows.coerceAtLeast(24)

        val emu = TerminalEmulator(columns, rows)
        emulator = emu

        val ren = TerminalRenderer(context, emu, colorScheme)
        ren.setTextSize(textSizeSp)
        renderer = ren

        emu.onUpdate = { post { invalidate() } }
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

    private data class NormalizedSelection(val sRow: Int, val sCol: Int, val eRow: Int, val eCol: Int)

    private fun getNormalizedSelection(): NormalizedSelection {
        val sRow: Int
        val sCol: Int
        val eRow: Int
        val eCol: Int
        
        if (selectionStartRow < selectionEndRow || 
            (selectionStartRow == selectionEndRow && selectionStartCol <= selectionEndCol)) {
            sRow = selectionStartRow
            sCol = selectionStartCol
            eRow = selectionEndRow
            eCol = selectionEndCol
        } else {
            sRow = selectionEndRow
            sCol = selectionEndCol
            eRow = selectionStartRow
            eCol = selectionStartCol
        }
        return NormalizedSelection(sRow, sCol, eRow, eCol)
    }

    private fun isCellSelected(col: Int, row: Int): Boolean {
        if (!isSelecting) return false
        
        val (sRow, sCol, eRow, eCol) = getNormalizedSelection()
        
        if (row < sRow || row > eRow) return false
        if (row > sRow && row < eRow) return true
        if (sRow == eRow) {
            return col in sCol..eCol
        }
        if (row == sRow) {
            return col >= sCol
        }
        if (row == eRow) {
            return col <= eCol
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = renderer
        r?.render(canvas, cursorVisible, scrollOffset)
        
        // Draw selection highlight on top
        if (isSelecting && termColumns > 0 && termRows > 0 && r != null) {
            for (row in 0 until termRows) {
                for (col in 0 until termColumns) {
                    if (isCellSelected(col, row)) {
                        canvas.drawRect(
                            col * r.fontWidth, row * r.fontLineSpacing,
                            (col + 1) * r.fontWidth, (row + 1) * r.fontLineSpacing,
                            selectionPaint
                        )
                    }
                }
            }

            // Draw selection handles
            val (sRow, sCol, eRow, eCol) = getNormalizedSelection()
            val x1 = sCol * r.fontWidth
            val y1 = (sRow + 1) * r.fontLineSpacing
            val x2 = (eCol + 1) * r.fontWidth
            val y2 = (eRow + 1) * r.fontLineSpacing

            val radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics)
            canvas.drawCircle(x1, y1, radius, handlePaint)
            canvas.drawCircle(x2, y2, radius, handlePaint)
        }

        // Draw scrollbar
        if (scrollbarAlpha > 0 && r != null && termRows > 0) {
            drawScrollbar(canvas, r)
        }
    }

    private fun drawScrollbar(canvas: Canvas, r: TerminalRenderer) {
        val sbCount = emulator?.buffer?.getScrollbackCount() ?: 0
        val totalLines = sbCount + termRows
        if (totalLines <= termRows) return

        val viewHeight = height.toFloat()
        val barHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrollbarMinHeightDp, resources.displayMetrics)
            .coerceAtMost(viewHeight * termRows / totalLines)
        val barWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrollbarWidthDp, resources.displayMetrics)

        // Position: scrollOffset=0 → bottom, scrollOffset=sbCount → top
        val scrollFraction = if (sbCount > 0) scrollOffset.toFloat() / sbCount else 0f
        val barTop = (viewHeight - barHeightPx) * (1f - scrollFraction)
        val barRight = width.toFloat()
        val barLeft = barRight - barWidthPx

        scrollbarPaint.alpha = scrollbarAlpha
        canvas.drawRoundRect(barLeft, barTop, barRight, barTop + barHeightPx, barWidthPx / 2, barWidthPx / 2, scrollbarPaint)
    }

    private fun showScrollbar() {
        scrollbarAlpha = 200
        handler.removeCallbacks(scrollbarFadeRunnable)
        handler.postDelayed(scrollbarFadeRunnable, scrollbarFadeDelay)
        invalidate()
    }

    private fun fadeOutScrollbar() {
        if (scrollbarAlpha <= 0) return
        scrollbarAlpha = (scrollbarAlpha - 20).coerceAtLeast(0)
        invalidate()
        if (scrollbarAlpha > 0) {
            handler.postDelayed(scrollbarFadeRunnable, 30) // ~30fps fade
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        val action = event.action
        lastTouchX = event.x
        lastTouchY = event.y
        val r = renderer

        // Handle scrolling
        if (r != null) {
            when (action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    scrollStartY = event.y
                    scrollStartOffset = scrollOffset
                    isScrolling = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - scrollStartY
                    if (!isScrolling && Math.abs(deltaY) > r.fontLineSpacing * 0.5f) {
                        isScrolling = true
                    }
                    if (isScrolling) {
                        val linesScrolled = (deltaY / r.fontLineSpacing).toInt()
                        val maxOffset = emulator?.buffer?.getScrollbackCount() ?: 0
                        scrollOffset = (scrollStartOffset - linesScrolled).coerceIn(0, maxOffset)
                        showScrollbar()
                        invalidate()
                        return true
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isScrolling) {
                        isScrolling = false
                        return true
                    }
                }
            }
        }

        if (isSelecting && r != null) {
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                val (sRow, sCol, eRow, eCol) = getNormalizedSelection()
                val x1 = sCol * r.fontWidth
                val y1 = (sRow + 1) * r.fontLineSpacing
                val x2 = (eCol + 1) * r.fontWidth
                val y2 = (eRow + 1) * r.fontLineSpacing

                val dx1 = event.x - x1
                val dy1 = event.y - y1
                val dist1 = Math.sqrt((dx1 * dx1 + dy1 * dy1).toDouble())

                val dx2 = event.x - x2
                val dy2 = event.y - y2
                val dist2 = Math.sqrt((dx2 * dx2 + dy2 * dy2).toDouble())

                val threshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, context.resources.displayMetrics)

                if (dist1 < threshold && dist1 < dist2) {
                    draggingHandle = 1
                    return true
                } else if (dist2 < threshold) {
                    draggingHandle = 2
                    return true
                } else {
                    // Touch outside handles: dismiss selection
                    actionMode?.finish()
                    isSelecting = false
                    invalidate()
                }
            } else if (action == android.view.MotionEvent.ACTION_MOVE) {
                val (col, row) = r.coordToColRow(event.x, event.y)
                if (draggingHandle == 1) {
                    selectionStartCol = col
                    selectionStartRow = row
                    invalidate()
                    return true
                } else if (draggingHandle == 2) {
                    selectionEndCol = col
                    selectionEndRow = row
                    invalidate()
                    return true
                }
            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                draggingHandle = 0
                return true
            }
        }

        if (action == android.view.MotionEvent.ACTION_DOWN) {
            requestFocus()
            showKeyboard()
        }
        return super.onTouchEvent(event)
    }

    // --- Keyboard input ---

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyDown(keyCode, event)
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
            KeyEvent.KEYCODE_ESCAPE -> {
                sendChar('\u001B')
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> sendEscape("[A")
            KeyEvent.KEYCODE_DPAD_DOWN -> sendEscape("[B")
            KeyEvent.KEYCODE_DPAD_RIGHT -> sendEscape("[C")
            KeyEvent.KEYCODE_DPAD_LEFT -> sendEscape("[D")
            KeyEvent.KEYCODE_MOVE_HOME -> {
                sendEscape("[H")
                return true
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                sendEscape("[F")
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP -> {
                sendEscape("[5~")
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                sendEscape("[6~")
                return true
            }
            else -> {
                val ch = event.unicodeChar
                if (ch != 0) {
                    val c = ch.toChar()
                    val ctrl = event.isCtrlPressed || isCtrlToggled
                    val alt = event.isAltPressed || isAltToggled

                    // Reset toggles after processing a character key
                    if (isCtrlToggled || isAltToggled) {
                        isCtrlToggled = false
                        isAltToggled = false
                    }

                    if (ctrl && c in 'a'..'z') {
                        sendChar((c - 'a' + 1).toChar())
                    } else if (alt) {
                        // Alt+char is sent as ESC followed by the char
                        sendEscape(c.toString())
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
            TerminalManager.writeToSession(it, ch.toString().toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Send text directly to the PTY session.
     */
    fun sendText(text: String) {
        session?.let {
            TerminalManager.writeToSession(it, text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun sendEscape(seq: String) {
        session?.let {
            TerminalManager.writeToSession(it, "\u001B$seq".toByteArray(Charsets.UTF_8))
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
                TerminalManager.writeToSession(it, text.toString().toByteArray(Charsets.UTF_8))
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
            if (event.action == KeyEvent.ACTION_DOWN) {
                view.onKeyDown(event.keyCode, event)
            }
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
     * Copy selected text to clipboard.
     */
    fun copySelection(): String? {
        if (selectionStartCol == -1 || selectionStartRow == -1 || 
            selectionEndCol == -1 || selectionEndRow == -1) return null

        val emu = emulator ?: return null
        val (sRow, sCol, eRow, eCol) = getNormalizedSelection()
        val sb = StringBuilder()
        
        for (r in sRow..eRow) {
            val line = emu.buffer.getScreenRow(r)
            val startC = if (r == sRow) sCol else 0
            val endC = if (r == eRow) eCol else emu.columns - 1
            
            val rowText = StringBuilder()
            for (c in startC..endC) {
                if (c < line.codePoints.size) {
                    val cp = line.codePoints[c]
                    if (cp != 0) {
                        rowText.appendCodePoint(cp)
                    } else {
                        rowText.append(' ')
                    }
                }
            }
            sb.append(rowText.toString().trimEnd())
            if (r < eRow) {
                sb.append("\n")
            }
        }
        
        val selectedText = sb.toString()
        if (selectedText.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("terminal_selection", selectedText)
            clipboard.setPrimaryClip(clip)
            return selectedText
        }
        return null
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
                TerminalManager.writeToSession(it, text.toString().toByteArray(Charsets.UTF_8))
            }
        }
    }

    /**
     * Show soft keyboard.
     */
    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Hide soft keyboard.
     */
    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    /**
     * Toggle soft keyboard visibility.
     */
    fun toggleKeyboard() {
        if (isFocused) {
            hideKeyboard()
            clearFocus()
        } else {
            requestFocus()
            showKeyboard()
        }
    }

    /**
     * Send control key sequence, respecting current modifier toggle state.
     */
    fun sendControlKey(keyCode: Int) {
        var metaState = 0
        if (isCtrlToggled) metaState = metaState or KeyEvent.META_CTRL_ON
        if (isAltToggled) metaState = metaState or KeyEvent.META_ALT_ON
        val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        onKeyDown(keyCode, event)
    }
}
