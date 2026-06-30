package dev.badapple.render.colorizers;

import dev.badapple.render.Colorizer;
import dev.badapple.render.Palette;

/**
 * Maps the downscaled luminance through a palette (a lookup table), turning the soft
 * anti-aliased edges into a colored glow — think fire or neon. Because every palette starts
 * at black, low luminance is naturally dark.
 */
public final class LuminanceLutColorizer implements Colorizer {

    private final Palette palette;

    public LuminanceLutColorizer(Palette palette) {
        this.palette = palette;
    }

    @Override
    public int rgb(double x01, double y01, double lum, double t) {
        if (lum <= 0) {
            return 0;
        }
        return palette.sample(lum);
    }
}
