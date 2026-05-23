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

        // Zero-width characters
        if (codePoint == 0x200B) return 0 // zero-width space
        if (codePoint == 0x200C || codePoint == 0x200D) return 0 // ZWNJ, ZWJ
        if (codePoint == 0xFEFF) return 0 // BOM / zero-width no-break space
        if (codePoint in 0xFE00..0xFE0F) return 0 // Variation Selectors
        if (codePoint in 0xE0100..0xE01EF) return 0 // Variation Selectors Supplement

        // Combining marks (should not advance cursor)
        if (codePoint in 0x0300..0x036F) return 0 // Combining Diacritical Marks
        if (codePoint in 0x1DC0..0x1DFF) return 0 // Combining Diacritical Marks Supplement
        if (codePoint in 0x20D0..0x20FF) return 0 // Combining Diacritical Marks for Symbols
        if (codePoint in 0xFE20..0xFE2F) return 0 // Combining Half Marks

        // Fast path for ASCII
        if (codePoint < 0x7f) return 1

        // Wide (EastAsianWidth W/F) — CJK ideographs, fullwidth forms, etc.
        if (isWide(codePoint)) return 2

        // Emoji (most are double-width in terminals)
        if (isEmojiWide(codePoint)) return 2

        return 1
    }

    private fun isWide(cp: Int): Boolean {
        // CJK Unified Ideographs
        if (cp in 0x4E00..0x9FFF) return true
        // CJK Unified Ideographs Extension A
        if (cp in 0x3400..0x4DBF) return true
        // CJK Unified Ideographs Extension B
        if (cp in 0x20000..0x2A6DF) return true
        // CJK Unified Ideographs Extension C
        if (cp in 0x2A700..0x2B73F) return true
        // CJK Unified Ideographs Extension D
        if (cp in 0x2B740..0x2B81F) return true
        // CJK Unified Ideographs Extension E
        if (cp in 0x2B820..0x2CEAF) return true
        // CJK Unified Ideographs Extension F
        if (cp in 0x2CEB0..0x2EBEF) return true
        // CJK Unified Ideographs Extension G
        if (cp in 0x30000..0x3134F) return true
        // CJK Unified Ideographs Extension H
        if (cp in 0x31350..0x323AF) return true
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
        // Kangxi Radicals
        if (cp in 0x2F00..0x2FDF) return true
        // Ideographic Description Characters
        if (cp in 0x2FF0..0x2FFF) return true
        // Symbols (some wide)
        if (cp in 0x3000..0x303F) return true
        return false
    }

    private fun isEmojiWide(cp: Int): Boolean {
        // Emoji in supplementary planes — most are double-width in terminals
        if (cp in 0x1F300..0x1F5FF) return true  // Miscellaneous Symbols and Pictographs
        if (cp in 0x1F600..0x1F64F) return true  // Emoticons
        if (cp in 0x1F680..0x1F6FF) return true  // Transport and Map Symbols
        if (cp in 0x1F700..0x1F77F) return true  // Alchemical Symbols
        if (cp in 0x1F780..0x1F7FF) return true  // Geometric Shapes Extended
        if (cp in 0x1F800..0x1F8FF) return true  // Supplemental Arrows-C
        if (cp in 0x1F900..0x1F9FF) return true  // Supplemental Symbols and Pictographs
        if (cp in 0x1FA00..0x1FA6F) return true  // Chess Symbols
        if (cp in 0x1FA70..0x1FAFF) return true  // Symbols and Pictographs Extended-A
        // Miscellaneous Symbols (some are wide)
        if (cp in 0x2600..0x26FF) return true
        // Dingbats (some are wide)
        if (cp in 0x2700..0x27BF) return true
        // Supplemental Symbols
        if (cp in 0x2B50..0x2B55) return true
        // Regional Indicator Symbols (flag pairs)
        if (cp in 0x1F1E0..0x1F1FF) return true
        return false
    }
}
