package dev.badapple.render;

/**
 * Maps a subpixel to a color. Bad Apple has no source color, so color is invented here
 * procedurally and kept entirely separate from the stored shape.
 *
 * <p><b>Contract:</b> the colorizer must fold luminance into its result, so that
 * {@code lum == 0} yields black. This keeps the background/letterbox black for every mode
 * and prevents color from bleeding past the shape — color is a pure function of the
 * already-resolved coverage, never a spatial mix.
 *
 * @param x01 normalized x in [0, 1] across the output
 * @param y01 normalized y in [0, 1] across the output
 * @param lum subpixel coverage in [0, 1]
 * @param t   playback progress in [0, 1] over the whole video
 * @return packed 0xRRGGBB
 */
@FunctionalInterface
public interface Colorizer {
    int rgb(double x01, double y01, double lum, double t);
}
