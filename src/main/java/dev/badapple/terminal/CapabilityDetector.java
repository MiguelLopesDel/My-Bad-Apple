package dev.badapple.terminal;

import dev.badapple.render.ColorDepth;
import org.jline.terminal.Terminal;

import java.util.Locale;

/**
 * Detects terminal capabilities in two stages: fast environment-variable heuristics, then
 * (unless disabled) active ANSI queries that ask the terminal directly. Everything degrades
 * safely — if a signal is missing, a conservative default is kept.
 */
public final class CapabilityDetector {

    private CapabilityDetector() {
    }

    public static TerminalCapabilities detect(Terminal terminal, boolean allowQueries) {
        TerminalCapabilities caps = new TerminalCapabilities();
        fromEnv(caps);
        if (allowQueries && isReal(terminal)) {
            try {
                AnsiQuery.refine(terminal, caps);
            } catch (Exception ignored) {
                // Queries are best-effort; keep the env-based guess on any failure.
            }
        }
        return caps;
    }

    private static void fromEnv(TerminalCapabilities caps) {
        String term = env("TERM");
        String prog = env("TERM_PROGRAM");
        String colorterm = env("COLORTERM");

        // Color depth.
        if (colorterm != null && (colorterm.contains("truecolor") || colorterm.contains("24bit"))) {
            caps.colorDepth = ColorDepth.TRUECOLOR;
        } else if (term != null && term.contains("256")) {
            caps.colorDepth = ColorDepth.ANSI256;
        } else if (term == null || term.equals("dumb")) {
            caps.colorDepth = ColorDepth.NONE;
        } else {
            caps.colorDepth = ColorDepth.ANSI16;
        }

        // Unicode from locale.
        String locale = firstNonNull(env("LC_ALL"), env("LC_CTYPE"), env("LANG"));
        caps.unicode = locale != null && locale.toLowerCase(Locale.ROOT).contains("utf");

        caps.termName = prog != null ? prog : (term != null ? term : "unknown");

        // Terminal identity and image protocols.
        if (env("KITTY_WINDOW_ID") != null || "xterm-kitty".equals(term)) {
            caps.kitty = true;
            caps.colorDepth = ColorDepth.TRUECOLOR;
            caps.termName = "kitty";
        }
        if ("iTerm.app".equals(prog)) {
            caps.iterm = true;
            caps.colorDepth = ColorDepth.TRUECOLOR;
            caps.termName = "iterm2";
        }
        if ("WezTerm".equalsIgnoreCase(prog) || env("WEZTERM_EXECUTABLE") != null) {
            caps.kitty = true;
            caps.colorDepth = ColorDepth.TRUECOLOR;
            caps.termName = "wezterm";
        }
        if (env("KONSOLE_VERSION") != null) {
            caps.sixel = true;
            caps.colorDepth = ColorDepth.TRUECOLOR;
            caps.termName = "konsole";
        }
        if (term != null && term.startsWith("foot")) {
            caps.sixel = true;
            caps.colorDepth = ColorDepth.TRUECOLOR;
            caps.termName = "foot";
        }
        if (env("VTE_VERSION") != null && caps.colorDepth != ColorDepth.NONE) {
            caps.colorDepth = ColorDepth.TRUECOLOR;
        }
        if (env("WT_SESSION") != null) {
            caps.colorDepth = ColorDepth.TRUECOLOR;
            caps.termName = "windows-terminal";
        }
        if ("alacritty".equalsIgnoreCase(prog) || (term != null && term.contains("alacritty"))) {
            caps.colorDepth = ColorDepth.TRUECOLOR;
            caps.termName = "alacritty";
        }

        // IDE terminals: usable but conservative.
        if ("vscode".equals(prog)) {
            caps.ide = true;
            caps.colorDepth = ColorDepth.TRUECOLOR;
            caps.termName = "vscode";
        }
        String emulator = env("TERMINAL_EMULATOR");
        if (emulator != null && emulator.contains("JediTerm")) {
            caps.ide = true;
            caps.termName = "intellij";
            caps.kitty = false;
            caps.sixel = false;
            caps.iterm = false;
            if (caps.colorDepth == ColorDepth.TRUECOLOR) {
                caps.colorDepth = ColorDepth.ANSI256;
            }
        }
    }

    private static boolean isReal(Terminal terminal) {
        if (terminal == null) {
            return false;
        }
        String type = terminal.getType();
        return type != null && !type.startsWith("dumb");
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
