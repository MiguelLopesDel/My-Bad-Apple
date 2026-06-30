package dev.badapple.render;

import dev.badapple.asset.Frame;

/**
 * Box-averages a 1-bit {@link Frame} down to a {@link GrayGrid} of a target size.
 *
 * <p>Averaging several source bits per output cell turns the binary shape into smooth
 * grayscale coverage — free anti-aliasing that makes edges look far better than a raw
 * threshold, and which the colorizers then turn into shading.
 */
public final class Downscaler {

    private Downscaler() {
    }

    /** Downscales {@code frame} into {@code out}; both retain the frame's aspect implicitly. */
    public static void downscale(Frame frame, GrayGrid out) {
        int srcW = frame.width;
        int srcH = frame.height;
        int dstW = out.width;
        int dstH = out.height;
        for (int dy = 0; dy < dstH; dy++) {
            int sy0 = (int) ((long) dy * srcH / dstH);
            int sy1 = Math.max(sy0 + 1, (int) ((long) (dy + 1) * srcH / dstH));
            for (int dx = 0; dx < dstW; dx++) {
                int sx0 = (int) ((long) dx * srcW / dstW);
                int sx1 = Math.max(sx0 + 1, (int) ((long) (dx + 1) * srcW / dstW));
                int lit = 0;
                int total = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    int row = sy * srcW;
                    for (int sx = sx0; sx < sx1; sx++) {
                        if (frame.get(row + sx)) {
                            lit++;
                        }
                        total++;
                    }
                }
                out.set(dx, dy, total == 0 ? 0f : (float) lit / total);
            }
        }
    }
}
