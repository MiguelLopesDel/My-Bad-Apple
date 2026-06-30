package dev.badapple.render;

/** Small color helpers shared by the colorizers. Colors are packed as 0xRRGGBB. */
public final class ColorUtil {

    private ColorUtil() {
    }

    public static int pack(int r, int g, int b) {
        return (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    /** Scales every channel by {@code f} (used to fold luminance into a base color). */
    public static int scale(int rgb, double f) {
        double k = clamp01(f);
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * k);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * k);
        int b = (int) Math.round((rgb & 0xFF) * k);
        return pack(r, g, b);
    }

    /** Linear interpolation between two packed colors. */
    public static int lerp(int a, int b, double f) {
        double k = clamp01(f);
        int ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba = a & 0xFF;
        int rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF;
        return pack(
                (int) Math.round(ra + (rb - ra) * k),
                (int) Math.round(ga + (gb - ga) * k),
                (int) Math.round(ba + (bb - ba) * k));
    }

    /** HSV to RGB. {@code h} wraps over [0,1); {@code s},{@code v} in [0,1]. */
    public static int hsv(double h, double s, double v) {
        h = h - Math.floor(h);
        double i = Math.floor(h * 6);
        double f = h * 6 - i;
        double p = v * (1 - s);
        double q = v * (1 - f * s);
        double tt = v * (1 - (1 - f) * s);
        double r, g, b;
        switch ((int) i % 6) {
            case 0 -> { r = v; g = tt; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = tt; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = tt; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return pack((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255));
    }

    public static double clamp01(double x) {
        return x < 0 ? 0 : Math.min(x, 1);
    }

    private static int clamp8(int x) {
        return x < 0 ? 0 : Math.min(x, 255);
    }
}
