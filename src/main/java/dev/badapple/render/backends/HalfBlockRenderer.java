package dev.badapple.render.backends;

import dev.badapple.render.Ansi;
import dev.badapple.render.AnsiColor;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Colorizer;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.Renderer;

/**
 * Universal text renderer using the upper-half-block glyph {@code ▀}: the glyph's
 * foreground color paints the top subpixel and the background the bottom one, doubling
 * vertical resolution. Emits SGR at the terminal's {@link ColorDepth} with per-cell diffing
 * and resets at each line end to prevent color bleed.
 */
public final class HalfBlockRenderer implements Renderer {

    private static final char UPPER_HALF = '▀';

    private final ColorDepth depth;

    public HalfBlockRenderer(ColorDepth depth) {
        this.depth = depth;
    }

    @Override
    public void render(GrayGrid grid, Colorizer colorizer, double t, StringBuilder out) {
        int cols = grid.width;
        int subRows = grid.height;
        int textRows = subRows / 2;
        int maxX = Math.max(1, cols - 1);
        int maxY = Math.max(1, subRows - 1);

        for (int r = 0; r < textRows; r++) {
            Ansi.moveTo(out, r + 1, 1);
            int lastFg = -1;
            int lastBg = -1;
            int yTop = r * 2;
            int yBot = r * 2 + 1;
            double yTopN = (double) yTop / maxY;
            double yBotN = (double) yBot / maxY;
            for (int c = 0; c < cols; c++) {
                double xN = (double) c / maxX;
                int fg = colorizer.rgb(xN, yTopN, grid.get(c, yTop), t) & 0xFFFFFF;
                int bg = colorizer.rgb(xN, yBotN, grid.get(c, yBot), t) & 0xFFFFFF;
                if (fg != lastFg) {
                    AnsiColor.appendFg(out, fg, depth);
                    lastFg = fg;
                }
                if (bg != lastBg) {
                    AnsiColor.appendBg(out, bg, depth);
                    lastBg = bg;
                }
                out.append(UPPER_HALF);
            }
            out.append(Ansi.RESET);
        }
    }
}
