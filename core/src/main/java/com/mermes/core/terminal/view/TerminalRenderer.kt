package com.mermes.core.terminal.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue

/**
 * Terminal rendering engine.
 * Draws terminal content to Canvas with optimized text runs.
 */
class TerminalRenderer(
    private val context: Context,
    private val emulator: TerminalEmulator,
    var colorScheme: TerminalColorScheme = TerminalColorScheme.DEFAULT
) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)
    }

    private val cursorPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val bgPaint = Paint()

    var fontWidth = 0f
        private set
    var fontLineSpacing = 0f
        private set
    var fontAscent = 0f
        private set

    init {
        updateFontMetrics()
    }

    private fun updateFontMetrics() {
        val fm = textPaint.fontMetrics
        fontAscent = -fm.ascent
        fontLineSpacing = fm.descent - fm.ascent + fm.leading
        fontWidth = textPaint.measureText("M")
    }

    /**
     * Set font size in sp.
     */
    fun setTextSize(sizeSp: Float) {
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics
        )
        updateFontMetrics()
    }

    /**
     * Render terminal content to canvas.
     * @param scrollOffset number of lines scrolled up from bottom (0 = at bottom)
     */
    fun render(canvas: Canvas, cursorVisible: Boolean, scrollOffset: Int = 0) {
        val rows = emulator.getRowCount()
        val cols = emulator.getColumnCount()
        val sbCount = emulator.buffer.getScrollbackCount()
        val offset = scrollOffset.coerceIn(0, sbCount)

        // Draw background
        bgPaint.color = colorScheme.background
        canvas.drawRect(0f, 0f, cols * fontWidth, rows * fontLineSpacing, bgPaint)

        for (row in 0 until rows) {
            // When offset=0: row 0 = screen[0], row rows-1 = screen[rows-1]
            // When offset=1: row 0 = scrollback[sbCount-1], row 1 = screen[0], ...
            // When offset=N: first N rows from scrollback (newest first), rest from screen
            val line = if (row < offset) {
                emulator.buffer.getScrollbackLine(sbCount - offset + row)
            } else {
                emulator.buffer.getScreenRow(row - offset)
            }
            if (line != null) {
                renderRow(canvas, row, line)
            }
        }

        // Draw cursor only when not scrolled
        if (cursorVisible && offset == 0) {
            drawCursor(canvas)
        }
    }

    private fun renderRow(canvas: Canvas, row: Int, line: TerminalRow) {
        val y = row * fontLineSpacing

        var col = 0
        while (col < emulator.columns) {
            val cp = line.codePoints[col]
            if (cp == 0) {
                col++
                continue
            }

            // Collect a text run: consecutive chars with same style
            val runStart = col
            val fg = line.fgColors[col]
            val bg = line.bgColors[col]
            val style = line.styles[col]

            col++
            while (col < emulator.columns) {
                if (line.codePoints[col] == 0) break
                if (line.fgColors[col] != fg || line.bgColors[col] != bg || line.styles[col] != style) break
                col++
            }
            val runEnd = col

            // Draw background for this run
            val bgColor = resolveBgColor(bg, style)
            if (bgColor != colorScheme.background) {
                bgPaint.color = bgColor
                canvas.drawRect(
                    runStart * fontWidth, y,
                    runEnd * fontWidth, y + fontLineSpacing,
                    bgPaint
                )
            }

            // Draw text
            val fgColor = resolveFgColor(fg, style)
            textPaint.color = fgColor
            textPaint.isFakeBoldText = (style.toInt() and TerminalRow.STYLE_BOLD.toInt()) != 0
            textPaint.isUnderlineText = (style.toInt() and TerminalRow.STYLE_UNDERLINE.toInt()) != 0

            // Build string from code points
            val sb = StringBuilder()
            for (i in runStart until runEnd) {
                val c = line.codePoints[i]
                if (c != 0) sb.appendCodePoint(c)
            }

            if (sb.isNotEmpty()) {
                canvas.drawTextRun(
                    sb, 0, sb.length, 0, sb.length,
                    runStart * fontWidth, y + fontAscent,
                    false, textPaint
                )
            }
        }
    }

    private fun drawCursor(canvas: Canvas) {
        val col = emulator.cursorCol.coerceIn(0, emulator.columns - 1)
        val row = emulator.cursorRow.coerceIn(0, emulator.rows - 1)
        val x = col * fontWidth
        val y = row * fontLineSpacing

        cursorPaint.color = colorScheme.cursor
        canvas.drawRect(x, y, x + fontWidth, y + fontLineSpacing, cursorPaint)

        // Draw the character under cursor with background color
        val line = emulator.buffer.getScreenRow(row)
        val cp = line.codePoints[col]
        if (cp != 0) {
            textPaint.color = colorScheme.background
            textPaint.isFakeBoldText = false
            textPaint.isUnderlineText = false
            val ch = String(Character.toChars(cp))
            canvas.drawTextRun(
                ch, 0, ch.length, 0, ch.length,
                x, y + fontAscent,
                false, textPaint
            )
        }
    }

    private fun resolveFgColor(colorIndex: Int, style: Byte): Int {
        val isInverse = (style.toInt() and TerminalRow.STYLE_INVERSE.toInt()) != 0
        val fg = if (isInverse) resolveRawBgColor(colorIndex) else resolveRawFgColor(colorIndex)
        val bg = if (isInverse) resolveRawFgColor(colorIndex) else resolveRawBgColor(colorIndex)
        // If inverse, swap
        return if (isInverse) bg else fg
    }

    private fun resolveBgColor(colorIndex: Int, style: Byte): Int {
        val isInverse = (style.toInt() and TerminalRow.STYLE_INVERSE.toInt()) != 0
        val fg = if (isInverse) resolveRawBgColor(colorIndex) else resolveRawFgColor(colorIndex)
        val bg = if (isInverse) resolveRawFgColor(colorIndex) else resolveRawBgColor(colorIndex)
        return if (isInverse) fg else bg
    }

    private fun resolveRawFgColor(colorIndex: Int): Int {
        return if (colorIndex == -1) colorScheme.foreground
        else if (colorIndex in colorScheme.ansiColors.indices) colorScheme.ansiColors[colorIndex]
        else colorScheme.foreground
    }

    private fun resolveRawBgColor(colorIndex: Int): Int {
        return if (colorIndex == -1) colorScheme.background
        else if (colorIndex in colorScheme.ansiColors.indices) colorScheme.ansiColors[colorIndex]
        else colorScheme.background
    }

    /**
     * Convert screen pixel coordinates to terminal col/row.
     */
    fun coordToColRow(x: Float, y: Float): Pair<Int, Int> {
        val col = (x / fontWidth).toInt().coerceIn(0, emulator.columns - 1)
        val row = (y / fontLineSpacing).toInt().coerceIn(0, emulator.rows - 1)
        return Pair(col, row)
    }
}
