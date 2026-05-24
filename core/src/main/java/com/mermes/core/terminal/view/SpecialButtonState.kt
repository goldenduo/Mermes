package com.mermes.core.terminal.view

/**
 * Maintains the state of a modifier key (Ctrl/Alt) with three modes:
 * - isCreated: button exists in the UI
 * - isActive: modifier is currently active (pressed)
 * - isLocked: modifier is locked on (long-press), stays active until tapped again
 *
 * State transitions:
 * - Short press: inactive <-> active (toggle)
 * - Long press: inactive -> locked (stays active until next tap)
 * - Tap while locked: locked -> inactive
 */
class SpecialButtonState {

    var isCreated = false
    var isActive = false
        private set
    var isLocked = false
        private set

    var onStateChanged: ((isActive: Boolean, isLocked: Boolean) -> Unit)? = null

    /** Toggle on short press: inactive -> active, active -> inactive, locked -> inactive */
    fun toggle() {
        if (isLocked) {
            isLocked = false
            isActive = false
        } else {
            isActive = !isActive
        }
        onStateChanged?.invoke(isActive, isLocked)
    }

    /** Lock on long press: active -> locked (stays active) */
    fun toggleLock() {
        if (isActive || isLocked) {
            isLocked = !isLocked
            if (!isLocked) {
                isActive = false
            }
        } else {
            // Not active — activate and lock
            isActive = true
            isLocked = true
        }
        onStateChanged?.invoke(isActive, isLocked)
    }

    /** Force deactivate (e.g., after sending a key) */
    fun deactivate() {
        if (!isLocked) {
            isActive = false
            onStateChanged?.invoke(isActive, isLocked)
        }
    }

    /** Reset to initial state */
    fun reset() {
        isActive = false
        isLocked = false
        onStateChanged?.invoke(isActive, isLocked)
    }
}
