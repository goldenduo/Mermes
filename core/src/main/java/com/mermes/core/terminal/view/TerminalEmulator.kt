package com.mermes.core.terminal.view

import com.mermes.common.log.MermesLog as Log

/**
 * Terminal emulator that parses ANSI escape sequences and maintains screen state.
 */
class TerminalEmulator(
    var columns: Int,
    var rows: Int
) {
    companion object {
        private const val TAG = "TerminalEmulator"

        // Parser states
        private const val STATE_NORMAL = 0
        private const val STATE_ESC = 1       // received ESC
        private const val STATE_CSI = 2       // received ESC [
        private const val STATE_CSI_PARAM = 3 // accumulating CSI params
    }

    val buffer = TerminalBuffer(columns, rows)

    // Cursor position (0-based)
    var cursorRow = 0
        private set
    var cursorCol = 0
        private set

    // Scroll region
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    // Current style
    private var currentFgColor = -1 // -1 = default
    private var currentBgColor = -1
    private var currentStyle: Byte = 0

    // Saved cursor (for ESC 7 / ESC 8)
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedFgColor = -1
    private var savedBgColor = -1
    private var savedStyle: Byte = 0

    // Parser state
    private var state = STATE_NORMAL
    private val csiParams = ArrayList<Int>()
    private var csiIntermediate = StringBuilder()

    // Callback for screen updates
    var onUpdate: (() -> Unit)? = null

    /**
     * Process terminal output data.
     */
    fun append(data: ByteArray) {
        for (b in data) {
            processByte(b.toInt() and 0xFF)
        }
        onUpdate?.invoke()
    }

    fun append(data: ByteArray, offset: Int, length: Int) {
        val end = offset + length
        for (i in offset until end) {
            processByte(data[i].toInt() and 0xFF)
        }
        onUpdate?.invoke()
    }

    private fun processByte(b: Int) {
        when (state) {
            STATE_NORMAL -> processNormal(b)
            STATE_ESC -> processEsc(b)
            STATE_CSI, STATE_CSI_PARAM -> processCsi(b)
        }
    }

    private fun processNormal(b: Int) {
        when (b) {
            0x1B -> state = STATE_ESC  // ESC
            0x08 -> { // Backspace
                if (cursorCol > 0) cursorCol--
            }
            0x09 -> { // Tab
                cursorCol = ((cursorCol / 8) + 1) * 8
                if (cursorCol >= columns) cursorCol = columns - 1
            }
            0x0A, 0x0B, 0x0C -> { // Line feed
                lineFeed()
            }
            0x0D -> { // Carriage return
                cursorCol = 0
                pendingWrap = false
            }
            in 0x20..0x7E, in 0x80..0xFF -> { // Printable (ASCII + extended)
                putChar(b)
            }
            // Other control chars ignored
        }
    }

    private fun processEsc(b: Int) {
        state = STATE_NORMAL
        when (b) {
            '['.code -> { // CSI
                state = STATE_CSI
                csiParams.clear()
                csiIntermediate.clear()
            }
            '7'.code -> { // Save cursor
                saveCursor()
            }
            '8'.code -> { // Restore cursor
                restoreCursor()
            }
            'M'.code -> { // Reverse index (scroll down)
                reverseIndex()
            }
            'D'.code -> { // Index (scroll up) — same as line feed
                lineFeed()
            }
            'c'.code -> { // Reset
                reset()
            }
            '='.code -> { // Application keypad mode (ignored)
            }
            '>'.code -> { // Normal keypad mode (ignored)
            }
            '('.code -> { // Charset selection G0 (ignored)
            }
            ')'.code -> { // Charset selection G1 (ignored)
            }
        }
    }

    private fun processCsi(b: Int) {
        when (b) {
            in '0'.code..'9'.code -> { // Parameter digit
                if (csiParams.isEmpty()) csiParams.add(0)
                val last = csiParams.last()
                csiParams[csiParams.size - 1] = last * 10 + (b - '0'.code)
                state = STATE_CSI_PARAM
            }
            ';'.code -> { // Parameter separator
                csiParams.add(0)
                state = STATE_CSI_PARAM
            }
            in 0x40..0x7E -> { // Final byte — execute
                executeCsi(b)
                state = STATE_NORMAL
            }
            else -> { // Intermediate byte
                csiIntermediate.append(b.toChar())
                state = STATE_CSI
            }
        }
    }

    private fun executeCsi(final: Int) {
        val p0 = csiParams.getOrElse(0) { 0 }
        val p1 = csiParams.getOrElse(1) { 0 }

        when (final) {
            'A'.code -> { cursorRow = (cursorRow - maxOf(p0, 1)).coerceAtLeast(scrollTop); pendingWrap = false }        // CUU
            'B'.code -> { cursorRow = (cursorRow + maxOf(p0, 1)).coerceAtMost(scrollBottom); pendingWrap = false }       // CUD
            'C'.code -> { cursorCol = (cursorCol + maxOf(p0, 1)).coerceAtMost(columns - 1); pendingWrap = false }       // CUF
            'D'.code -> { cursorCol = (cursorCol - maxOf(p0, 1)).coerceAtLeast(0); pendingWrap = false }                 // CUB
            'H'.code, 'f'.code -> { // CUP — cursor position
                cursorRow = (maxOf(p0, 1) - 1).coerceIn(0, rows - 1)
                cursorCol = (maxOf(p1, 1) - 1).coerceIn(0, columns - 1)
                pendingWrap = false
            }
            'J'.code -> eraseInDisplay(p0)  // ED
            'K'.code -> eraseInLine(p0)     // EL
            'L'.code -> insertLines(p0)     // IL
            'M'.code -> deleteLines(p0)     // DL
            'P'.code -> deleteChars(maxOf(p0, 1)) // DCH
            '@'.code -> insertChars(maxOf(p0, 1)) // ICH
            'S'.code -> scrollUpN(maxOf(p0, 1))   // SU
            'T'.code -> scrollDownN(maxOf(p0, 1)) // SD
            'X'.code -> eraseChars(maxOf(p0, 1))  // ECH
            'd'.code -> cursorRow = (maxOf(p0, 1) - 1).coerceIn(0, rows - 1)                  // VPA
            'G'.code -> cursorCol = (maxOf(p0, 1) - 1).coerceIn(0, columns - 1)               // CHA
            'm'.code -> executeSgr()        // SGR
            'r'.code -> { // DECSTBM — set scroll region
                scrollTop = (maxOf(p0, 1) - 1).coerceIn(0, rows - 1)
                scrollBottom = (if (p1 == 0) rows else maxOf(p1, 1) - 1).coerceIn(scrollTop, rows - 1)
                cursorRow = 0
                cursorCol = 0
            }
            'h'.code -> { } // Set mode (ignored for now)
            'l'.code -> { } // Reset mode (ignored for now)
        }
    }

    private fun executeSgr() {
        if (csiParams.isEmpty()) {
            csiParams.add(0)
        }

        var i = 0
        while (i < csiParams.size) {
            when (val p = csiParams[i]) {
                0 -> { currentFgColor = -1; currentBgColor = -1; currentStyle = 0 } // Reset
                1 -> currentStyle = (currentStyle.toInt() or TerminalRow.STYLE_BOLD.toInt()).toByte()
                2 -> currentStyle = (currentStyle.toInt() or TerminalRow.STYLE_DIM.toInt()).toByte()
                3 -> currentStyle = (currentStyle.toInt() or TerminalRow.STYLE_ITALIC.toInt()).toByte()
                4 -> currentStyle = (currentStyle.toInt() or TerminalRow.STYLE_UNDERLINE.toInt()).toByte()
                7 -> currentStyle = (currentStyle.toInt() or TerminalRow.STYLE_INVERSE.toInt()).toByte()
                22 -> currentStyle = (currentStyle.toInt() and (TerminalRow.STYLE_BOLD.toInt() or TerminalRow.STYLE_DIM.toInt()).inv()).toByte()
                23 -> currentStyle = (currentStyle.toInt() and TerminalRow.STYLE_ITALIC.toInt().inv()).toByte()
                24 -> currentStyle = (currentStyle.toInt() and TerminalRow.STYLE_UNDERLINE.toInt().inv()).toByte()
                27 -> currentStyle = (currentStyle.toInt() and TerminalRow.STYLE_INVERSE.toInt().inv()).toByte()
                in 30..37 -> currentFgColor = p - 30  // Standard foreground
                in 40..47 -> currentBgColor = p - 40  // Standard background
                38 -> { // Extended foreground
                    if (i + 1 < csiParams.size && csiParams[i + 1] == 5 && i + 2 < csiParams.size) {
                        currentFgColor = csiParams[i + 2]
                        i += 2
                    }
                }
                48 -> { // Extended background
                    if (i + 1 < csiParams.size && csiParams[i + 1] == 5 && i + 2 < csiParams.size) {
                        currentBgColor = csiParams[i + 2]
                        i += 2
                    }
                }
                39 -> currentFgColor = -1 // Default foreground
                49 -> currentBgColor = -1 // Default background
                in 90..97 -> currentFgColor = p - 90 + 8  // Bright foreground
                in 100..107 -> currentBgColor = p - 100 + 8 // Bright background
            }
            i++
        }
    }

    private var pendingWrap = false

    private fun putChar(codePoint: Int) {
        val width = WcWidth.width(codePoint)
        if (width <= 0) return

        // Deferred wrap: if cursor was at the rightmost column, wrap now
        if (pendingWrap) {
            buffer.getScreenRow(cursorRow).isWrapped = true
            lineFeed()
            cursorCol = 0
            pendingWrap = false
        }

        // Wrap if needed
        if (cursorCol + width > columns) {
            buffer.getScreenRow(cursorRow).isWrapped = true
            lineFeed()
            cursorCol = 0
        }

        buffer.getScreenRow(cursorRow).setChar(cursorCol, codePoint, currentFgColor, currentBgColor, currentStyle)

        // For double-width chars, mark the second column as continuation
        if (width == 2 && cursorCol + 1 < columns) {
            buffer.getScreenRow(cursorRow).setChar(cursorCol + 1, 0, currentFgColor, currentBgColor, currentStyle)
        }

        cursorCol += width

        // Don't advance cursor past the last column; defer the wrap
        if (cursorCol >= columns) {
            cursorCol = columns - 1
            pendingWrap = true
        }
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            buffer.scrollUp(scrollTop, scrollBottom)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) {
            buffer.scrollDown(scrollTop, scrollBottom)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> { // Erase below
                eraseInLine(0)
                for (i in (cursorRow + 1) until rows) {
                    buffer.getScreenRow(i).clear()
                }
            }
            1 -> { // Erase above
                for (i in 0 until cursorRow) {
                    buffer.getScreenRow(i).clear()
                }
                eraseInLine(1)
            }
            2 -> { // Erase all
                for (i in 0 until rows) {
                    buffer.getScreenRow(i).clear()
                }
            }
        }
    }

    private fun eraseInLine(mode: Int) {
        val row = buffer.getScreenRow(cursorRow)
        when (mode) {
            0 -> { // Erase to right
                for (i in cursorCol until columns) {
                    row.setChar(i, 0, -1, -1, 0)
                }
            }
            1 -> { // Erase to left
                for (i in 0..cursorCol) {
                    row.setChar(i, 0, -1, -1, 0)
                }
            }
            2 -> { // Erase entire line
                row.clear()
            }
        }
    }

    private fun insertLines(count: Int) {
        for (i in 0 until count) {
            buffer.scrollDown(cursorRow, scrollBottom)
        }
    }

    private fun deleteLines(count: Int) {
        for (i in 0 until count) {
            buffer.scrollUp(cursorRow, scrollBottom)
        }
    }

    private fun deleteChars(count: Int) {
        val row = buffer.getScreenRow(cursorRow)
        val n = minOf(count, columns - cursorCol)
        for (i in cursorCol until columns - n) {
            row.codePoints[i] = row.codePoints[i + n]
            row.fgColors[i] = row.fgColors[i + n]
            row.bgColors[i] = row.bgColors[i + n]
            row.styles[i] = row.styles[i + n]
        }
        for (i in (columns - n) until columns) {
            row.setChar(i, 0, -1, -1, 0)
        }
    }

    private fun insertChars(count: Int) {
        val row = buffer.getScreenRow(cursorRow)
        val n = minOf(count, columns - cursorCol)
        for (i in (columns - 1) downTo (cursorCol + n)) {
            row.codePoints[i] = row.codePoints[i - n]
            row.fgColors[i] = row.fgColors[i - n]
            row.bgColors[i] = row.bgColors[i - n]
            row.styles[i] = row.styles[i - n]
        }
        for (i in cursorCol until (cursorCol + n)) {
            row.setChar(i, 0, -1, -1, 0)
        }
    }

    private fun eraseChars(count: Int) {
        val row = buffer.getScreenRow(cursorRow)
        val n = minOf(count, columns - cursorCol)
        for (i in cursorCol until (cursorCol + n)) {
            row.setChar(i, 0, -1, -1, 0)
        }
    }

    private fun scrollUpN(count: Int) {
        for (i in 0 until count) {
            buffer.scrollUp(scrollTop, scrollBottom)
        }
    }

    private fun scrollDownN(count: Int) {
        for (i in 0 until count) {
            buffer.scrollDown(scrollTop, scrollBottom)
        }
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedFgColor = currentFgColor
        savedBgColor = currentBgColor
        savedStyle = currentStyle
    }

    private fun restoreCursor() {
        cursorRow = savedCursorRow
        cursorCol = savedCursorCol
        currentFgColor = savedFgColor
        currentBgColor = savedBgColor
        currentStyle = savedStyle
    }

    /**
     * Resize the terminal.
     */
    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) return

        buffer.resize(newColumns, newRows)
        columns = newColumns
        rows = newRows
        scrollBottom = rows - 1

        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, columns - 1)
    }

    /**
     * Reset terminal to initial state.
     */
    fun reset() {
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        currentFgColor = -1
        currentBgColor = -1
        currentStyle = 0
        state = STATE_NORMAL

        for (i in 0 until rows) {
            buffer.getScreenRow(i).clear()
        }
    }

    fun getColumnCount(): Int = columns
    fun getRowCount(): Int = rows
}
