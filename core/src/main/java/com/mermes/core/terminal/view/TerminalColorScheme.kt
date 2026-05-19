package com.mermes.core.terminal.view

import android.graphics.Color

/**
 * Terminal color scheme with default Termux-like colors.
 */
data class TerminalColorScheme(
    val foreground: Int,
    val background: Int,
    val cursor: Int,
    val selection: Int,
    val ansiColors: IntArray
) {
    companion object {
        val DEFAULT = TerminalColorScheme(
            foreground = Color.rgb(0xE0, 0xE0, 0xE0),
            background = Color.rgb(0x00, 0x00, 0x00),
            cursor = Color.rgb(0xFF, 0xFF, 0xFF),
            selection = Color.argb(0x40, 0xFF, 0xFF, 0xFF),
            ansiColors = intArrayOf(
                // Standard colors (0-7)
                Color.rgb(0x00, 0x00, 0x00), // black
                Color.rgb(0xCC, 0x00, 0x00), // red
                Color.rgb(0x4E, 0x9A, 0x06), // green
                Color.rgb(0xC4, 0xA0, 0x00), // yellow
                Color.rgb(0x34, 0x65, 0xA4), // blue
                Color.rgb(0x75, 0x50, 0x7B), // magenta
                Color.rgb(0x06, 0x98, 0x9A), // cyan
                Color.rgb(0xD3, 0xD7, 0xCF), // white
                // Bright colors (8-15)
                Color.rgb(0x55, 0x57, 0x53), // bright black
                Color.rgb(0xEF, 0x29, 0x29), // bright red
                Color.rgb(0x8A, 0xE2, 0x34), // bright green
                Color.rgb(0xFC, 0xE9, 0x4F), // bright yellow
                Color.rgb(0x72, 0x9F, 0xCF), // bright blue
                Color.rgb(0xAD, 0x7F, 0xA8), // bright magenta
                Color.rgb(0x34, 0xE2, 0xE2), // bright cyan
                Color.rgb(0xEE, 0xEE, 0xEC)  // bright white
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalColorScheme) return false
        return foreground == other.foreground && background == other.background &&
                cursor == other.cursor && selection == other.selection &&
                ansiColors.contentEquals(other.ansiColors)
    }

    override fun hashCode(): Int {
        var result = foreground
        result = 31 * result + background
        result = 31 * result + cursor
        result = 31 * result + selection
        result = 31 * result + ansiColors.contentHashCode()
        return result
    }
}
