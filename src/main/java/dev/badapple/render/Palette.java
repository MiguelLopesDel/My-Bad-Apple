package dev.badapple.render;

import java.util.Locale;

/**
 * An ordered list of anchor colors sampled by linear interpolation. Used by the gradient
 * and luminance-LUT colorizers. The named palettes below are starting points meant to be
 * tweaked — these are your colors to make your own. Every palette begins at black so a
 * luminance of 0 stays black.
 */
public final class Palette {

    private final int[] anchors;

    public Palette(int... anchors) {
        if (anchors.length < 2) {
            throw new IllegalArgumentException("a palette needs at least two anchors");
        }
        this.anchors = anchors;
    }

    /** Samples the palette at {@code pos} in [0,1], interpolating between anchors. */
    public int sample(double pos) {
        double p = ColorUtil.clamp01(pos) * (anchors.length - 1);
        int i = (int) Math.floor(p);
        if (i >= anchors.length - 1) {
            return anchors[anchors.length - 1];
        }
        return ColorUtil.lerp(anchors[i], anchors[i + 1], p - i);
    }

    public static final Palette FIRE = new Palette(
            0x000000, 0x420a00, 0xa31200, 0xff5a00, 0xffc400, 0xffffe0);
    public static final Palette NEON = new Palette(
            0x000000, 0x2a0a4a, 0x7a1fa2, 0xe83bd6, 0x39e6ff);
    public static final Palette AURORA = new Palette(
            0x000000, 0x05303a, 0x0aa17a, 0x4be37a, 0x9b6bff);
    public static final Palette ICE = new Palette(
            0x000000, 0x06203f, 0x1d6fb8, 0x6fd0ff, 0xffffff);

    /** Resolves a palette by name (case-insensitive), defaulting to {@code fallback}. */
    public static Palette byName(String name, Palette fallback) {
        if (name == null) {
            return fallback;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "fire" -> FIRE;
            case "neon" -> NEON;
            case "aurora" -> AURORA;
            case "ice" -> ICE;
            default -> fallback;
        };
    }
}
