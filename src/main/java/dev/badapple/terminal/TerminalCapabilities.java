package dev.badapple.terminal;

import dev.badapple.render.ColorDepth;

/** What the current terminal can do. Filled by {@link CapabilityDetector}. */
public final class TerminalCapabilities {

    public String termName = "unknown";
    public ColorDepth colorDepth = ColorDepth.ANSI16;
    public boolean unicode = false;
    public boolean kitty = false;
    public boolean sixel = false;
    public boolean iterm = false;
    public boolean ide = false;
    /** Cell size in pixels (0 when unknown); used to size the image backends. */
    public int cellWidthPx = 0;
    public int cellHeightPx = 0;

    public boolean hasImageProtocol() {
        return kitty || sixel || iterm;
    }

    @Override
    public String toString() {
        return """
                terminal      : %s
                color depth   : %s
                unicode       : %s
                image kitty   : %s
                image sixel   : %s
                image iterm   : %s
                ide terminal  : %s
                cell size px  : %dx%d""".formatted(
                termName, colorDepth, unicode, kitty, sixel, iterm, ide, cellWidthPx, cellHeightPx);
    }
}
