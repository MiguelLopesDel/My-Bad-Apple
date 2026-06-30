package dev.badapple.render.backends;

import dev.badapple.render.Ansi;

import java.util.BitSet;

/**
 * Renders each frame as sixel graphics. Pixels are quantized to the 216-color (6×6×6) cube so
 * they fit sixel's palette registers, then encoded band by band (six rows at a time), one
 * color pass per band, with run-length compression. Sixels draw at native pixel size, so the
 * image is sized to roughly fill the terminal's pixel area.
 */
public final class SixelRenderer extends ImageRenderer {

    // xterm 6x6x6 cube levels and their percentage equivalents (0..100) for color registers.
    private static final int[] LEVELS = {0, 95, 135, 175, 215, 255};
    private static final int[] LEVEL_PCT = {0, 37, 53, 69, 84, 100};

    @Override
    protected void encode(int[] argb, int width, int height, StringBuilder out) {
        int[] idx = new int[width * height];
        BitSet used = new BitSet(216);
        for (int i = 0; i < idx.length; i++) {
            int rgb = argb[i];
            int ci = 36 * level((rgb >> 16) & 0xFF) + 6 * level((rgb >> 8) & 0xFF) + level(rgb & 0xFF);
            idx[i] = ci;
            used.set(ci);
        }

        // DCS q + raster attributes (1:1 pixel aspect, explicit size).
        out.append(Ansi.ESC).append("Pq");
        out.append("\"1;1;").append(width).append(';').append(height);

        // Define only the color registers that actually appear.
        for (int c = used.nextSetBit(0); c >= 0; c = used.nextSetBit(c + 1)) {
            int r = c / 36, g = (c / 6) % 6, b = c % 6;
            out.append('#').append(c).append(";2;")
                    .append(LEVEL_PCT[r]).append(';').append(LEVEL_PCT[g]).append(';').append(LEVEL_PCT[b]);
        }

        int[] colorsInBand = new int[216];
        for (int y0 = 0; y0 < height; y0 += 6) {
            int rowsInBand = Math.min(6, height - y0);

            int bandColorCount = collectBandColors(idx, width, y0, rowsInBand, colorsInBand);
            for (int k = 0; k < bandColorCount; k++) {
                int color = colorsInBand[k];
                out.append('#').append(color);
                emitColorRow(out, idx, width, y0, rowsInBand, color);
                if (k < bandColorCount - 1) {
                    out.append('$'); // graphics carriage return: overlay next color in this band
                }
            }
            out.append('-'); // graphics newline: next band
        }
        out.append(Ansi.ESC).append('\\');
    }

    private static int collectBandColors(int[] idx, int width, int y0, int rows, int[] outColors) {
        BitSet present = new BitSet(216);
        for (int row = 0; row < rows; row++) {
            int base = (y0 + row) * width;
            for (int x = 0; x < width; x++) {
                present.set(idx[base + x]);
            }
        }
        int n = 0;
        for (int c = present.nextSetBit(0); c >= 0; c = present.nextSetBit(c + 1)) {
            outColors[n++] = c;
        }
        return n;
    }

    private void emitColorRow(StringBuilder out, int[] idx, int width, int y0, int rows, int color) {
        char prev = 0;
        int run = 0;
        for (int x = 0; x < width; x++) {
            int bits = 0;
            for (int row = 0; row < rows; row++) {
                if (idx[(y0 + row) * width + x] == color) {
                    bits |= 1 << row;
                }
            }
            char ch = (char) (63 + bits);
            if (ch == prev) {
                run++;
            } else {
                emitRun(out, run, prev);
                prev = ch;
                run = 1;
            }
        }
        emitRun(out, run, prev);
    }

    private static void emitRun(StringBuilder out, int count, char ch) {
        if (count <= 0) {
            return;
        }
        if (count < 4) {
            for (int k = 0; k < count; k++) {
                out.append(ch);
            }
        } else {
            out.append('!').append(count).append(ch);
        }
    }

    private static int level(int v) {
        if (v < 47) {
            return 0;
        }
        if (v < 115) {
            return 1;
        }
        if (v < 155) {
            return 2;
        }
        if (v < 195) {
            return 3;
        }
        if (v < 235) {
            return 4;
        }
        return 5;
    }
}
