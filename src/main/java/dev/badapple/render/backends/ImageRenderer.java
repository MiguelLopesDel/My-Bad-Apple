package dev.badapple.render.backends;

import dev.badapple.render.Ansi;
import dev.badapple.render.Colorizer;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.ImageComposer;
import dev.badapple.render.Renderer;

/**
 * Base for renderers that emit the frame as a real image. They ask for a full pixel canvas
 * (cell size × terminal size, capped), compose an RGB buffer, and let the subclass encode it
 * into a terminal image protocol.
 */
abstract class ImageRenderer implements Renderer {

    private static final int DEFAULT_CELL_W = 8;
    private static final int DEFAULT_CELL_H = 16;
    private static final int MAX_PIXELS = 900_000; // bound encode/transmit work per frame

    /** Cell footprint the image should occupy, captured from the last gridSize() call. */
    protected int cols = 1;
    protected int rows = 1;

    private int[] argb = new int[0];

    @Override
    public GridSize gridSize(int cols, int rows, int cellWidthPx, int cellHeightPx) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        int cw = cellWidthPx > 0 ? cellWidthPx : DEFAULT_CELL_W;
        int ch = cellHeightPx > 0 ? cellHeightPx : DEFAULT_CELL_H;
        long w = (long) this.cols * cw;
        long h = (long) this.rows * ch;
        double scale = Math.min(1.0, Math.sqrt((double) MAX_PIXELS / (w * h)));
        int gw = Math.max(1, (int) (w * scale));
        int gh = Math.max(1, (int) (h * scale));
        return new GridSize(gw, gh);
    }

    @Override
    public void render(GrayGrid grid, Colorizer colorizer, double t, StringBuilder out) {
        int n = grid.width * grid.height;
        if (argb.length != n) {
            argb = new int[n];
        }
        ImageComposer.compose(grid, colorizer, t, argb);
        out.append(Ansi.HOME);
        encode(argb, grid.width, grid.height, out);
    }

    /** Encodes the RGB buffer into this backend's image protocol. */
    protected abstract void encode(int[] argb, int width, int height, StringBuilder out);
}
