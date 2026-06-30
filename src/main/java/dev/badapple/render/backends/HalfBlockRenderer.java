package dev.badapple.render.backends;

import dev.badapple.render.Ansi;
import dev.badapple.render.AnsiColor;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Colorizer;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.LineRenderer;

/**
 * Universal text renderer using the upper-half-block glyph {@code ▀}: the glyph's
 * foreground color paints the top subpixel and the background the bottom one, doubling
 * vertical resolution. Emits SGR at the terminal's {@link ColorDepth} with per-cell diffing
 * and resets at each line end to prevent color bleed. Each row is self-contained, so the
 * player can skip rows that didn't change.
 */
public final class HalfBlockRenderer implements LineRenderer {

    private static final char UPPER_HALF = '▀';

    private final ColorDepth depth;
    private final StringBuilder line = new StringBuilder(256);

    public HalfBlockRenderer(ColorDepth depth) {
        this.depth = depth;
    }

    @Override
    public int lineCount(GrayGrid grid) {
        return grid.height / 2;
    }

    @Override
    public void renderLines(GrayGrid grid, Colorizer colorizer, double t, String[] out) {
        int cols = grid.width;
        int subRows = grid.height;
        int textRows = subRows / 2;
        int maxX = Math.max(1, cols - 1);
        int maxY = Math.max(1, subRows - 1);

        for (int r = 0; r < textRows; r++) {
            line.setLength(0);
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
                    AnsiColor.appendFg(line, fg, depth);
                    lastFg = fg;
                }
                if (bg != lastBg) {
                    AnsiColor.appendBg(line, bg, depth);
                    lastBg = bg;
                }
                line.append(UPPER_HALF);
            }
            line.append(Ansi.RESET);
            out[r] = line.toString();
        }
    }

    @Override
    public void render(GrayGrid grid, Colorizer colorizer, double t, StringBuilder out) {
        String[] rows = new String[lineCount(grid)];
        renderLines(grid, colorizer, t, rows);
        for (int r = 0; r < rows.length; r++) {
            Ansi.moveTo(out, r + 1, 1);
            out.append(rows[r]);
        }
    }
}
