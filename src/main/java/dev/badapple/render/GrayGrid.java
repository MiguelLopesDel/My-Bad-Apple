package dev.badapple.render;

import java.util.Arrays;

/**
 * A grid of grayscale coverage values in [0, 1], row-major. This is the downscaled,
 * anti-aliased form that renderers consume — one value per output subpixel (a half-block
 * cell holds two stacked subpixels). Color is applied later by a {@link Colorizer}.
 */
public final class GrayGrid {

    public final int width;
    public final int height;
    private final float[] data;

    public GrayGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.data = new float[width * height];
    }

    public float get(int x, int y) {
        return data[y * width + x];
    }

    public void set(int x, int y, float value) {
        data[y * width + x] = value;
    }

    public void fill(float value) {
        Arrays.fill(data, value);
    }
}
