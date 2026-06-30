package dev.badapple.render;

/** How much color the terminal can show. Drives how SGR color escapes are emitted. */
public enum ColorDepth {
    /** No color: renderers fall back to a luminance ramp. */
    NONE,
    /** Basic 16-color palette. */
    ANSI16,
    /** 256-color (xterm) palette. */
    ANSI256,
    /** 24-bit direct color. */
    TRUECOLOR
}
