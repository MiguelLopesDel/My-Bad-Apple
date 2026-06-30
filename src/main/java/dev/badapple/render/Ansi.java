package dev.badapple.render;

/** Centralized ANSI escape sequences. ESC is the numeric 27, so there are no invisible bytes in source. */
public final class Ansi {

    public static final char ESC = 27;
    public static final String CSI = "" + ESC + '[';
    public static final String RESET = CSI + "0m";
    public static final String HIDE_CURSOR = CSI + "?25l";
    public static final String SHOW_CURSOR = CSI + "?25h";
    public static final String ALT_SCREEN_ON = CSI + "?1049h";
    public static final String ALT_SCREEN_OFF = CSI + "?1049l";
    public static final String CLEAR = CSI + "2J";
    public static final String HOME = CSI + "H";

    private Ansi() {
    }

    /** Appends a "move cursor to (row, col)" sequence (1-based). */
    public static void moveTo(StringBuilder sb, int row, int col) {
        sb.append(CSI).append(row).append(';').append(col).append('H');
    }
}
