package dev.badapple.render;

/**
 * A renderer that produces output one text row at a time. Each row is a self-contained string
 * (no cursor positioning, and SGR is reset at the end), so the player can compare rows against
 * the previous frame and rewrite only the ones that changed — a big win for content like Bad
 * Apple, where large bands stay static frame to frame.
 */
public interface LineRenderer extends Renderer {

    /** Number of text rows this renderer emits for the given grid. */
    int lineCount(GrayGrid grid);

    /**
     * Renders each text row into {@code out} (which must hold at least {@link #lineCount}
     * entries). Each entry is independent of the others, so rows may be emitted in any order or
     * skipped entirely.
     */
    void renderLines(GrayGrid grid, Colorizer colorizer, double t, String[] out);
}
