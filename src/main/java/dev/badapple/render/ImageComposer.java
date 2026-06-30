package dev.badapple.render;

/**
 * Builds an RGB pixel buffer from a colorized grid — the shared first step of every image
 * backend. One grid cell becomes one pixel; the colorizer folds luminance so empty cells
 * stay black.
 */
public final class ImageComposer {

    private ImageComposer() {
    }

    /** Fills {@code argb} (length width*height) with 0xFFRRGGBB pixels for the grid. */
    public static void compose(GrayGrid grid, Colorizer colorizer, double t, int[] argb) {
        int w = grid.width;
        int h = grid.height;
        int maxX = Math.max(1, w - 1);
        int maxY = Math.max(1, h - 1);
        for (int y = 0; y < h; y++) {
            double yN = (double) y / maxY;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int rgb = colorizer.rgb((double) x / maxX, yN, grid.get(x, y), t) & 0xFFFFFF;
                argb[row + x] = 0xFF000000 | rgb;
            }
        }
    }
}
