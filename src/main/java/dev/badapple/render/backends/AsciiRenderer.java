package dev.badapple.render.backends;

import dev.badapple.render.Ansi;
import dev.badapple.render.AnsiColor;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Colorizer;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.LineRenderer;

/**
 * Fallback renderer for terminals without Unicode half-blocks. One character per cell from a
 * luminance density ramp; optionally tinted when the terminal has color. Averages the two
 * subpixels of each text row so it shares the same grid as the half-block renderer. Each row
 * is self-contained, so the player can skip rows that didn't change.
 */
public final class AsciiRenderer implements LineRenderer {

    private static final String RAMP = " .:-=+*#%@";

    private final ColorDepth depth;
    private final StringBuilder line = new StringBuilder(256);

    public AsciiRenderer(ColorDepth depth) {
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
        boolean colored = depth != ColorDepth.NONE;

        for (int r = 0; r < textRows; r++) {
            line.setLength(0);
            int lastFg = -1;
            int yTop = r * 2;
            int yBot = r * 2 + 1;
            double yN = (double) yTop / maxY;
            for (int c = 0; c < cols; c++) {
                double lum = (grid.get(c, yTop) + grid.get(c, yBot)) * 0.5;
                int idx = Math.min(RAMP.length() - 1, (int) Math.round(lum * (RAMP.length() - 1)));
                if (colored) {
                    int fg = colorizer.rgb((double) c / maxX, yN, lum, t) & 0xFFFFFF;
                    if (fg != lastFg) {
                        AnsiColor.appendFg(line, fg, depth);
                        lastFg = fg;
                    }
                }
                line.append(RAMP.charAt(idx));
            }
            if (colored) {
                line.append(Ansi.RESET);
            }
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
