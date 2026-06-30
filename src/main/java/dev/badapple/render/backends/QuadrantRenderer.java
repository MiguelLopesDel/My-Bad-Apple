package dev.badapple.render.backends;

import dev.badapple.render.Ansi;
import dev.badapple.render.AnsiColor;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Colorizer;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.LineRenderer;

/**
 * Higher-resolution text renderer using quadrant block glyphs. Each character cell packs a
 * 2x2 grid of subpixels (versus the half-block's 1x2), doubling horizontal detail. A cell
 * still carries only two colors — foreground for the "lit" subpixels, background for the rest
 * — which is plenty for Bad Apple's near-binary, anti-aliased shape: solid regions become
 * full blocks and edges split into the two dominant grays. Each row is self-contained so the
 * player can skip rows that didn't change.
 */
public final class QuadrantRenderer implements LineRenderer {

    // Indexed by a 4-bit mask: bit0=top-left, bit1=top-right, bit2=bottom-left, bit3=bottom-right.
    private static final char[] GLYPHS = {
            ' ',       // 0000 (none)
            '▘',  // 0001 TL        ▘
            '▝',  // 0010 TR        ▝
            '▀',  // 0011 TL TR     ▀
            '▖',  // 0100 BL        ▖
            '▌',  // 0101 TL BL     ▌
            '▞',  // 0110 TR BL     ▞
            '▛',  // 0111 TL TR BL  ▛
            '▗',  // 1000 BR        ▗
            '▚',  // 1001 TL BR     ▚
            '▐',  // 1010 TR BR     ▐
            '▜',  // 1011 TL TR BR  ▜
            '▄',  // 1100 BL BR     ▄
            '▙',  // 1101 TL BL BR  ▙
            '▟',  // 1110 TR BL BR  ▟
            '█',  // 1111 (all)     █
    };

    private final ColorDepth depth;
    private final StringBuilder line = new StringBuilder(256);

    public QuadrantRenderer(ColorDepth depth) {
        this.depth = depth;
    }

    @Override
    public GridSize gridSize(int cols, int rows, int cellWidthPx, int cellHeightPx) {
        // Two subpixels per cell in each axis.
        return new GridSize(Math.max(1, cols) * 2, Math.max(1, rows) * 2);
    }

    @Override
    public int lineCount(GrayGrid grid) {
        return grid.height / 2;
    }

    @Override
    public void renderLines(GrayGrid grid, Colorizer colorizer, double t, String[] out) {
        int cellCols = grid.width / 2;
        int textRows = grid.height / 2;
        int maxX = Math.max(1, grid.width - 1);
        int maxY = Math.max(1, grid.height - 1);

        for (int r = 0; r < textRows; r++) {
            line.setLength(0);
            int lastFg = -1;
            int lastBg = -1;
            int yTop = r * 2;
            int yBot = r * 2 + 1;
            double yN = (yTop + 1.0) / maxY;
            for (int c = 0; c < cellCols; c++) {
                int xL = c * 2;
                int xR = c * 2 + 1;
                float tl = grid.get(xL, yTop);
                float tr = grid.get(xR, yTop);
                float bl = grid.get(xL, yBot);
                float br = grid.get(xR, yBot);

                float min = Math.min(Math.min(tl, tr), Math.min(bl, br));
                float max = Math.max(Math.max(tl, tr), Math.max(bl, br));
                float thr = (min + max) * 0.5f;

                int mask = 0;
                float litSum = 0f;
                float dimSum = 0f;
                int litN = 0;
                int dimN = 0;
                if (tl >= thr) { mask |= 1; litSum += tl; litN++; } else { dimSum += tl; dimN++; }
                if (tr >= thr) { mask |= 2; litSum += tr; litN++; } else { dimSum += tr; dimN++; }
                if (bl >= thr) { mask |= 4; litSum += bl; litN++; } else { dimSum += bl; dimN++; }
                if (br >= thr) { mask |= 8; litSum += br; litN++; } else { dimSum += br; dimN++; }

                double fgLum = litN > 0 ? litSum / litN : 0.0;
                double bgLum = dimN > 0 ? dimSum / dimN : 0.0;
                double xN = (xL + 1.0) / maxX;
                int fg = colorizer.rgb(xN, yN, fgLum, t) & 0xFFFFFF;
                int bg = colorizer.rgb(xN, yN, bgLum, t) & 0xFFFFFF;

                if (fg != lastFg) {
                    AnsiColor.appendFg(line, fg, depth);
                    lastFg = fg;
                }
                if (bg != lastBg) {
                    AnsiColor.appendBg(line, bg, depth);
                    lastBg = bg;
                }
                line.append(GLYPHS[mask]);
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
