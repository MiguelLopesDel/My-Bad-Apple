package dev.badapple.render;

/**
 * Turns a colorized {@link GrayGrid} into terminal output. Implementations differ only in
 * the final "writer" stage: half-blocks/ASCII for text cells, or image protocols
 * (kitty/sixel/iTerm) for pixel-perfect terminals.
 */
public interface Renderer {

    /**
     * The grid resolution this renderer wants for a terminal of the given size. Text
     * renderers use one column wide and two subpixels per row; image renderers ask for a
     * full pixel canvas.
     */
    default GridSize gridSize(int cols, int rows, int cellWidthPx, int cellHeightPx) {
        return new GridSize(cols, rows * 2);
    }

    /**
     * Renders one full grid into {@code out}, positioning its own content so the caller only
     * needs to home the cursor beforehand.
     *
     * @param grid      full-screen grid sized per {@link #gridSize}
     * @param colorizer color source; folds luminance so empty cells stay black
     * @param t         playback progress in [0, 1]
     * @param out       sink for the escape/character stream
     */
    void render(GrayGrid grid, Colorizer colorizer, double t, StringBuilder out);

    /** Grid dimensions in cells/pixels. */
    record GridSize(int width, int height) {
    }
}
