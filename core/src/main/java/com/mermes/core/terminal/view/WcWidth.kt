package com.mermes.core.terminal.view

/**
 * Unicode character width calculation (wcwidth equivalent).
 * Returns the number of terminal columns a character occupies.
 * CJK/fullwidth characters return 2, most others return 1.
 */
object WcWidth {

    fun width(codePoint: Int): Int {
        if (codePoint == 0) return 0
        if (codePoint < 32 || (codePoint in 0x7f..0x9f)) return -1 // control chars
        if (codePoint == 0x200B) return 0 // zero-width space

        // Fast path for ASCII
        if (codePoint < 0x7f) return 1

        // Wide (EastAsianWidth W/F) — CJK ideographs, fullwidth forms, etc.
        if (isWide(codePoint)) return 2

        return 1
    }

    private fun isWide(cp: Int): Boolean {
        // CJK Unified Ideographs
        if (cp in 0x4E00..0x9FFF) return true
        // CJK Unified Ideographs Extension A
        if (cp in 0x3400..0x4DBF) return true
        // CJK Unified Ideographs Extension B
        if (cp in 0x20000..0x2A6DF) return true
        // CJK Compatibility Ideographs
        if (cp in 0xF900..0xFAFF) return true
        // CJK Compatibility Forms
        if (cp in 0xFE30..0xFE4F) return true
        // Fullwidth Forms
        if (cp in 0xFF01..0xFF60) return true
        if (cp in 0xFFE0..0xFFE6) return true
        // Hangul Syllables
        if (cp in 0xAC00..0xD7AF) return true
        // Hangul Jamo
        if (cp in 0x1100..0x115F) return true
        // Bopomofo
        if (cp in 0x3100..0x312F) return true
        if (cp in 0x31A0..0x31BF) return true
        // Katakana / Hiragana
        if (cp in 0x3040..0x309F) return true
        if (cp in 0x30A0..0x30FF) return true
        // Enclosed CJK
        if (cp in 0x3200..0x32FF) return true
        // CJK Radicals
        if (cp in 0x2E80..0x2EFF) return true
        if (cp in 0x2F00..0x2FDF) return true
        // Kangxi Radicals
        if (cp in 0x2F00..0x2FDF) return true
        // Ideographic Description Characters
        if (cp in 0x2FF0..0x2FFF) return true
        // Symbols (some wide)
        if (cp in 0x3000..0x303F) return true
        return false
    }
}
