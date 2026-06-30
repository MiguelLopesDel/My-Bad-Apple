package dev.badapple.render.colorizers;

import dev.badapple.render.ColorUtil;
import dev.badapple.render.Colorizer;

/**
 * The silhouette's hue rotates over the course of the video, with a gentle spatial phase so
 * the color also drifts across the frame. Value is the luminance, so empty cells stay black.
 */
public final class HueCycleColorizer implements Colorizer {

    private final double cycles;
    private final double spatialPhase;

    public HueCycleColorizer() {
        this(3.0, 0.15);
    }

    public HueCycleColorizer(double cycles, double spatialPhase) {
        this.cycles = cycles;
        this.spatialPhase = spatialPhase;
    }

    @Override
    public int rgb(double x01, double y01, double lum, double t) {
        if (lum <= 0) {
            return 0;
        }
        double hue = t * cycles + (x01 + y01) * spatialPhase;
        return ColorUtil.hsv(hue, 1.0, ColorUtil.clamp01(lum));
    }
}
