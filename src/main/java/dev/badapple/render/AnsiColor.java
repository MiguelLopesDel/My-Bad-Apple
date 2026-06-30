package dev.badapple.render;

/**
 * Emits SGR color escapes for a given {@link ColorDepth}, downconverting 24-bit colors to
 * the 256- or 16-color palettes when the terminal can't do truecolor.
 */
public final class AnsiColor {

    private AnsiColor() {
    }

    public static void appendFg(StringBuilder sb, int rgb, ColorDepth depth) {
        append(sb, rgb, depth, true);
    }

    public static void appendBg(StringBuilder sb, int rgb, ColorDepth depth) {
        append(sb, rgb, depth, false);
    }

    private static void append(StringBuilder sb, int rgb, ColorDepth depth, boolean fg) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        switch (depth) {
            case TRUECOLOR -> sb.append(Ansi.CSI).append(fg ? "38;2;" : "48;2;")
                    .append(r).append(';').append(g).append(';').append(b).append('m');
            case ANSI256 -> sb.append(Ansi.CSI).append(fg ? "38;5;" : "48;5;")
                    .append(to256(r, g, b)).append('m');
            case ANSI16 -> sb.append(Ansi.CSI).append(fg ? "38;5;" : "48;5;")
                    .append(to16(r, g, b)).append('m');
            case NONE -> { /* no color */ }
        }
    }

    /** Maps an RGB color to the xterm 256-color cube (or grayscale ramp). */
    public static int to256(int r, int g, int b) {
        // Grays get the dedicated 24-step ramp for smoother shading.
        if (Math.abs(r - g) < 8 && Math.abs(g - b) < 8) {
            if (r < 8) {
                return 16;
            }
            if (r > 248) {
                return 231;
            }
            return 232 + (r - 8) * 24 / 247;
        }
        int ri = component6(r);
        int gi = component6(g);
        int bi = component6(b);
        return 16 + 36 * ri + 6 * gi + bi;
    }

    private static int component6(int v) {
        if (v < 48) {
            return 0;
        }
        if (v < 115) {
            return 1;
        }
        return (v - 35) / 40;
    }

    /** Maps an RGB color to the nearest of the 16 basic ANSI colors. */
    public static int to16(int r, int g, int b) {
        int luma = (r * 30 + g * 59 + b * 11) / 100;
        int bright = luma > 170 ? 8 : 0;
        int threshold = luma > 90 ? 90 : 64;
        int bits = (r > threshold ? 1 : 0) | (g > threshold ? 2 : 0) | (b > threshold ? 4 : 0);
        if (bits == 0 && bright == 8) {
            return 7; // dim white rather than bright black
        }
        return bits + bright;
    }
}
