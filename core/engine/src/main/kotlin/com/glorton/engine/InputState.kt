package com.glorton.engine

/**
 * Normalized per-player input for one logic tick.
 * Maps to the original key bindings (frame_42):
 *  P1: A/D left-right, W jump, S crouch, J punch, K special, Shift shield.
 *  P2: arrow keys + numpad 0/1/2.
 */
data class InputState(
    val left: Boolean = false,
    val right: Boolean = false,
    val up: Boolean = false,
    val down: Boolean = false,
    val punch: Boolean = false,
    val special: Boolean = false,
    val shield: Boolean = false,
) {
    companion object {
        val NONE = InputState()
    }
}
