package dev.badapple.render.backends;

import dev.badapple.render.Ansi;
import dev.badapple.render.AnsiColor;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Colorizer;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.Renderer;

/**
 * Fallback renderer for terminals without Unicode half-blocks. One character per cell from a
 * luminance density ramp; optionally tinted when the terminal has color. Averages the two
 * subpixels of each text row so it shares the same grid as the half-block renderer.
 */
public final class AsciiRenderer implements Renderer {

    private static final String RAMP = " .:-=+*#%@";

    private final ColorDepth depth;

    public AsciiRenderer(ColorDepth depth) {
        this.depth = depth;
    }

    @Override
    public void render(GrayGrid grid, Colorizer colorizer, double t, StringBuilder out) {
        int cols = grid.width;
        int subRows = grid.height;
        int textRows = subRows / 2;
        int maxX = Math.max(1, cols - 1);
        int maxY = Math.max(1, subRows - 1);
        boolean colored = depth != ColorDepth.NONE;

        for (int r = 0; r < textRows; r++) {
            Ansi.moveTo(out, r + 1, 1);
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
                        AnsiColor.appendFg(out, fg, depth);
                        lastFg = fg;
                    }
                }
                out.append(RAMP.charAt(idx));
            }
            if (colored) {
                out.append(Ansi.RESET);
            }
        }
    }
}
