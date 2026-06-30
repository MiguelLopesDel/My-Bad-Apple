package dev.badapple.render.colorizers;

import dev.badapple.render.Colorizer;

/**
 * Classic black-and-white: luminance maps straight to a gray level. The downscaler's
 * anti-aliasing shows through as smooth gray edges rather than hard pixels.
 */
public final class MonoColorizer implements Colorizer {

    @Override
    public int rgb(double x01, double y01, double lum, double t) {
        int v = (int) Math.round(Math.max(0, Math.min(1, lum)) * 255);
        return (v << 16) | (v << 8) | v;
    }
}
