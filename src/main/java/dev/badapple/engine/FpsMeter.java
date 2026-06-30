package dev.badapple.engine;

/**
 * Measures the true presentation rate: how many distinct frames are actually pushed to the
 * terminal per second. Each {@link #record} marks one frame hitting the screen, and
 * {@link #fps} reports the rate over a sliding ~1s window — so it reflects real playback speed
 * (dropped frames and a slow terminal included), not the asset's target frame rate.
 */
public final class FpsMeter {

    private static final long WINDOW_NANOS = 1_000_000_000L;

    private final long[] stamps = new long[256];
    private int head;
    private int size;

    /** Marks one frame as presented at the given {@link System#nanoTime()} reading. */
    public void record(long nowNanos) {
        stamps[head] = nowNanos;
        head = (head + 1) % stamps.length;
        if (size < stamps.length) {
            size++;
        }
    }

    /** Frames presented per second over the last ~1s, or 0 with fewer than two samples. */
    public double fps(long nowNanos) {
        long cutoff = nowNanos - WINDOW_NANOS;
        long newest = Long.MIN_VALUE;
        long oldest = Long.MAX_VALUE;
        int count = 0;
        for (int i = 0; i < size; i++) {
            long ts = stamps[(head - 1 - i + stamps.length) % stamps.length];
            if (ts < cutoff) {
                break; // samples are recorded in order, so everything older follows
            }
            newest = Math.max(newest, ts);
            oldest = Math.min(oldest, ts);
            count++;
        }
        if (count < 2 || newest == oldest) {
            return 0.0;
        }
        return (count - 1) * 1_000_000_000.0 / (newest - oldest);
    }
}
