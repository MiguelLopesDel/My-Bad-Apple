package dev.badapple.render;

/**
 * Turns a colorized {@link GrayGrid} into terminal output. Implementations differ only in
 * the final "writer" stage: half-blocks/ASCII for text cells, or image protocols
 * (kitty/sixel/iTerm) for pixel-perfect terminals.
 */
public interface Renderer {

    /**
     * Renders one full-screen grid into {@code out}, positioning its own lines so the
     * caller only needs to home the cursor beforehand.
     *
     * @param grid       full-screen grid; for half-block renderers its height is even
     *                   (two subpixels per text row)
     * @param colorizer  color source; folds luminance so empty cells stay black
     * @param t          playback progress in [0, 1]
     * @param out        sink for the escape/character stream
     */
    void render(GrayGrid grid, Colorizer colorizer, double t, StringBuilder out);
}
