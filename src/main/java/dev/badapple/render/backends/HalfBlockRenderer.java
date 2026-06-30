package dev.badapple.render.backends;

import dev.badapple.render.Colorizer;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.Renderer;

/**
 * Universal text renderer using the upper-half-block glyph {@code ▀}: the glyph's
 * foreground color paints the top subpixel and the background color paints the bottom one,
 * doubling vertical resolution. Emits truecolor SGR with per-cell diffing (a color escape
 * is written only when it changes) and resets at each line end to prevent color bleed.
 */
public final class HalfBlockRenderer implements Renderer {

    private static final char UPPER_HALF = '▀';
    private static final String CSI = "[";

    @Override
    public void render(GrayGrid grid, Colorizer colorizer, double t, StringBuilder out) {
        int cols = grid.width;
        int subRows = grid.height;
        int textRows = subRows / 2;
        int maxX = Math.max(1, cols - 1);
        int maxY = Math.max(1, subRows - 1);

        for (int r = 0; r < textRows; r++) {
            out.append(CSI).append(r + 1).append(";1H");
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
                    appendColor(out, true, fg);
                    lastFg = fg;
                }
                if (bg != lastBg) {
                    appendColor(out, false, bg);
                    lastBg = bg;
                }
                out.append(UPPER_HALF);
            }
            out.append(CSI).append("0m");
        }
    }

    private static void appendColor(StringBuilder out, boolean foreground, int rgb) {
        out.append(CSI).append(foreground ? "38;2;" : "48;2;")
                .append((rgb >> 16) & 0xFF).append(';')
                .append((rgb >> 8) & 0xFF).append(';')
                .append(rgb & 0xFF).append('m');
    }
}
