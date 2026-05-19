package com.mermes.core.terminal.view

/**
 * Terminal screen buffer with scrollback support.
 * Manages visible rows and a scrollback history ring buffer.
 */
class TerminalBuffer(
    var columns: Int,
    var rows: Int,
    private val maxScrollback: Int = 1000
) {
    /** Active screen rows (size = rows) */
    private var screenRows = Array(rows) { TerminalRow(columns) }

    /** Scrollback ring buffer */
    private val scrollback = ArrayList<TerminalRow>(maxScrollback)
    private var scrollbackStart = 0
    private var scrollbackCount = 0

    fun getScreenRow(row: Int): TerminalRow {
        return screenRows[row]
    }

    /**
     * Scroll the screen up by one line.
     * The top row moves to scrollback; a new blank row appears at the bottom.
     */
    fun scrollUp(scrollTop: Int, scrollBottom: Int) {
        val topRow = screenRows[scrollTop]
        topRow.isWrapped = false

        // Move to scrollback
        if (scrollTop == 0) {
            addToScrollback(topRow)
        }

        // Shift rows up
        val removed = screenRows[scrollTop]
        for (i in scrollTop until scrollBottom) {
            screenRows[i] = screenRows[i + 1]
        }
        screenRows[scrollBottom] = removed
        removed.clear()
    }

    /**
     * Scroll the screen down by one line.
     * A new blank row appears at the top; the bottom row is discarded.
     */
    fun scrollDown(scrollTop: Int, scrollBottom: Int) {
        val removed = screenRows[scrollBottom]
        for (i in scrollBottom downTo scrollTop + 1) {
            screenRows[i] = screenRows[i - 1]
        }
        screenRows[scrollTop] = removed
        removed.clear()
    }

    /**
     * Resize the buffer, preserving existing content where possible.
     */
    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) return

        val newScreenRows = Array(newRows) { TerminalRow(newColumns) }
        val copyRows = minOf(rows, newRows)
        val copyCols = minOf(columns, newColumns)

        for (i in 0 until copyRows) {
            val src = screenRows[i]
            val dst = newScreenRows[i]
            for (j in 0 until copyCols) {
                dst.codePoints[j] = src.codePoints[j]
                dst.fgColors[j] = src.fgColors[j]
                dst.bgColors[j] = src.bgColors[j]
                dst.styles[j] = src.styles[j]
            }
        }

        // Replace internal arrays
        screenRows = newScreenRows
        columns = newColumns
        rows = newRows
        // This is a simplified resize — in production we'd reflow wrapped lines
    }

    fun clearScrollback() {
        scrollback.clear()
        scrollbackStart = 0
        scrollbackCount = 0
    }

    fun getScrollbackCount(): Int = scrollbackCount

    fun getScrollbackLine(index: Int): TerminalRow? {
        if (index < 0 || index >= scrollbackCount) return null
        val actualIndex = (scrollbackStart + index) % maxScrollback
        return scrollback[actualIndex]
    }

    private fun addToScrollback(row: TerminalRow) {
        if (scrollback.size < maxScrollback) {
            val newRow = TerminalRow(columns)
            newRow.copyFrom(row)
            scrollback.add(newRow)
            scrollbackCount++
        } else {
            val recycled = scrollback[scrollbackStart]
            recycled.copyFrom(row)
            scrollbackStart = (scrollbackStart + 1) % maxScrollback
        }
    }
}
