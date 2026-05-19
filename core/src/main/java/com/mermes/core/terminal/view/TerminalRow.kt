package com.mermes.core.terminal.view

/**
 * A single row in the terminal screen buffer.
 * Stores code points and their style attributes.
 */
class TerminalRow(val columns: Int) {

    /** Unicode code points for each column */
    val codePoints = IntArray(columns)

    /** Foreground color (ANSI color index or -1 for default) */
    val fgColors = IntArray(columns) { -1 }

    /** Background color (ANSI color index or -1 for default) */
    val bgColors = IntArray(columns) { -1 }

    /** Style flags: bit 0=bold, bit 1=underline, bit 2=italic, bit 3=inverse, bit 4=dim */
    val styles = ByteArray(columns)

    /** True if this row has a continuation (wrapped from previous line) */
    var isWrapped = false

    fun clear() {
        codePoints.fill(0)
        fgColors.fill(-1)
        bgColors.fill(-1)
        styles.fill(0)
        isWrapped = false
    }

    fun setChar(col: Int, codePoint: Int, fgColor: Int, bgColor: Int, style: Byte) {
        if (col in 0 until columns) {
            codePoints[col] = codePoint
            fgColors[col] = fgColor
            bgColors[col] = bgColor
            styles[col] = style
        }
    }

    fun copyFrom(other: TerminalRow) {
        val len = minOf(columns, other.columns)
        other.codePoints.copyInto(codePoints, 0, 0, len)
        other.fgColors.copyInto(fgColors, 0, 0, len)
        other.bgColors.copyInto(bgColors, 0, 0, len)
        other.styles.copyInto(styles, 0, 0, len)
        isWrapped = other.isWrapped
    }

    companion object {
        const val STYLE_BOLD: Byte = 1
        const val STYLE_UNDERLINE: Byte = 2
        const val STYLE_ITALIC: Byte = 4
        const val STYLE_INVERSE: Byte = 8.toByte()
        const val STYLE_DIM: Byte = 16.toByte()
    }
}
