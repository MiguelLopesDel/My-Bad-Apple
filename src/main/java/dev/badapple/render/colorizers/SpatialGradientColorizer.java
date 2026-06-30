package dev.badapple.render.colorizers;

import dev.badapple.render.ColorUtil;
import dev.badapple.render.Colorizer;
import dev.badapple.render.Palette;

/**
 * Colors the silhouette by position using a palette, along a direction that slowly rotates
 * and drifts over time — like a colored backdrop sweeping behind the figure. The sampled
 * color is scaled by luminance so the background stays black and nothing bleeds past the shape.
 */
public final class SpatialGradientColorizer implements Colorizer {

    private final Palette palette;
    private final double driftSpeed;

    public SpatialGradientColorizer(Palette palette) {
        this(palette, 0.2);
    }

    public SpatialGradientColorizer(Palette palette, double driftSpeed) {
        this.palette = palette;
        this.driftSpeed = driftSpeed;
    }

    @Override
    public int rgb(double x01, double y01, double lum, double t) {
        if (lum <= 0) {
            return 0;
        }
        double angle = t * 2 * Math.PI * 0.05;
        double cx = x01 - 0.5;
        double cy = y01 - 0.5;
        double proj = (cx * Math.cos(angle) + cy * Math.sin(angle)) / 1.4142 + 0.5;
        double pos = proj + t * driftSpeed;
        pos -= Math.floor(pos);
        return ColorUtil.scale(palette.sample(pos), lum);
    }
}
