package dev.badapple.cli;

import dev.badapple.render.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive start menu shown when the jar is launched with no options. Lets the user pick a
 * color mode, palette, renderer and audio toggle with the arrow keys, then translates the
 * choices into the same {@code --flag} argv that {@link Args} parses. The defaults (mono +
 * auto renderer + audio) are exactly what a bare run would have used, so pressing Enter is the
 * "just play it" path.
 */
public final class Launcher {

    private static final String BOLD = Ansi.CSI + "1m";
    private static final String DIM = Ansi.CSI + "2m";
    private static final String REVERSE = Ansi.CSI + "7m";

    private static final String[] COLORS = {"mono", "hue", "gradient", "lut"};
    private static final String[] PALETTES = {"aurora", "fire", "neon", "ice"};
    private static final String[] RENDERERS =
            {"auto", "quadrant", "halfblock", "ascii", "kitty", "sixel", "iterm"};
    private static final String[] AUDIO = {"on", "off"};

    private static final String[] COLOR_HINTS = {
            "classic black & white",
            "rainbow that cycles over time",
            "palette swept across space",
            "palette mapped by brightness",
    };
    private static final String[] RENDERER_HINTS = {
            "best for your terminal (image if it can)",
            "2x2 blocks — sharp but blocky edges",
            "1x2 blocks — smooth grayscale",
            "plain characters, no Unicode",
            "kitty image protocol — full resolution",
            "sixel image protocol — full resolution",
            "iTerm image protocol — full resolution",
    };

    private static final int FIELD_COUNT = 4;

    private int color;
    private int palette;
    private int renderer;
    private int audio;
    private int sel;

    /** Shows the menu. Returns the chosen argv, or {@code null} if the user chose to quit. */
    public String[] run() throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        String type = terminal.getType();
        if (type == null || type.equals("dumb")) {
            terminal.close();
            return new String[0]; // no real TTY: fall through to defaults
        }

        PrintWriter w = terminal.writer();
        Attributes saved = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        try {
            w.print(Ansi.ALT_SCREEN_ON);
            w.print(Ansi.HIDE_CURSOR);
            while (true) {
                draw(w, Math.max(48, terminal.getWidth()));
                int c = reader.read(); // blocking single-key read is reliable across terminals
                if (c < 0 || c == 'q' || c == 'Q' || c == 3) {
                    return null;
                }
                if (c == '\r' || c == '\n') {
                    return buildArgv();
                }
                if (c == 27) {
                    if (handleEscape(reader)) {
                        return null; // lone ESC
                    }
                    continue;
                }
                handleKey(c);
            }
        } finally {
            terminal.setAttributes(saved);
            w.print(Ansi.SHOW_CURSOR);
            w.print(Ansi.ALT_SCREEN_OFF);
            w.flush();
            terminal.close();
        }
    }

    /** Handles an escape sequence (arrows). Returns true on a lone ESC (quit). */
    private boolean handleEscape(NonBlockingReader reader) throws Exception {
        int c2 = reader.read(200);
        if (c2 == NonBlockingReader.READ_EXPIRED) {
            return true;
        }
        if (c2 == '[' || c2 == 'O') {
            switch (reader.read(200)) {
                case 'A' -> sel = (sel + FIELD_COUNT - 1) % FIELD_COUNT; // up
                case 'B' -> sel = (sel + 1) % FIELD_COUNT;               // down
                case 'C' -> change(1);                                   // right
                case 'D' -> change(-1);                                  // left
                default -> { /* ignore */ }
            }
        }
        return false;
    }

    private void handleKey(int c) {
        switch (c) {
            case 'k', 'w' -> sel = (sel + FIELD_COUNT - 1) % FIELD_COUNT;
            case 'j', 's' -> sel = (sel + 1) % FIELD_COUNT;
            case 'h' -> change(-1);
            case 'l', ' ' -> change(1);
            default -> { /* ignore */ }
        }
    }

    /** Cycles the currently selected field by {@code dir} (wrapping). */
    private void change(int dir) {
        switch (sel) {
            case 0 -> color = wrap(color + dir, COLORS.length);
            case 1 -> palette = wrap(palette + dir, PALETTES.length);
            case 2 -> renderer = wrap(renderer + dir, RENDERERS.length);
            case 3 -> audio = wrap(audio + dir, AUDIO.length);
            default -> { /* none */ }
        }
    }

    private static int wrap(int i, int n) {
        return ((i % n) + n) % n;
    }

    private String[] buildArgv() {
        List<String> a = new ArrayList<>();
        a.add("--color");
        a.add(COLORS[color]);
        a.add("--palette");
        a.add(PALETTES[palette]);
        a.add("--renderer");
        a.add(RENDERERS[renderer]);
        if (AUDIO[audio].equals("off")) {
            a.add("--no-audio");
        }
        return a.toArray(new String[0]);
    }

    private void draw(PrintWriter w, int cols) {
        int margin = Math.max(2, (cols - 52) / 2);
        StringBuilder b = new StringBuilder(1024);
        b.append(Ansi.CLEAR);

        row(b, 2, margin, BOLD + "My Bad Apple" + Ansi.RESET
                + DIM + "  ·  terminal player" + Ansi.RESET);

        boolean paletteActive = color == 2 || color == 3; // gradient or lut
        field(b, 5, margin, 0, "Color", COLORS[color], COLOR_HINTS[color]);
        field(b, 6, margin, 1, "Palette", PALETTES[palette],
                paletteActive ? "used now" : "used by gradient / lut");
        field(b, 7, margin, 2, "Renderer", RENDERERS[renderer], RENDERER_HINTS[renderer]);
        field(b, 8, margin, 3, "Audio", AUDIO[audio], "");

        row(b, 10, margin, DIM + "Enter play   up/down select   left/right change   q quit"
                + Ansi.RESET);
        row(b, 11, margin, DIM + "tip: just press Enter for the classic mono look" + Ansi.RESET);
        w.print(b);
        w.flush();
    }

    private void field(StringBuilder b, int rowN, int margin, int index, String label,
                       String value, String hint) {
        boolean active = sel == index;
        StringBuilder line = new StringBuilder();
        line.append(active ? BOLD + "> " + Ansi.RESET : "  ");
        line.append(pad(label, 9)).append(' ');
        String cell = "< " + value + " >";
        line.append(active ? REVERSE + cell + Ansi.RESET : cell);
        if (!hint.isEmpty()) {
            line.append(DIM).append("   ").append(hint).append(Ansi.RESET);
        }
        row(b, rowN, margin, line.toString());
    }

    private static void row(StringBuilder b, int rowN, int col, String content) {
        Ansi.moveTo(b, rowN, col);
        b.append(content);
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        return s + " ".repeat(width - s.length());
    }
}
