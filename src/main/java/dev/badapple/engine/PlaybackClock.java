package dev.badapple.engine;

/**
 * Source of playback time in seconds. Backed by audio when available (so video tracks the
 * real sound output) or by the wall clock when running silently.
 */
public interface PlaybackClock {

    double seconds();

    /** Wall-clock fallback used when there is no audio. Supports pausing. */
    final class Wall implements PlaybackClock {
        private long baseNanos = System.nanoTime();
        private long pausedAtNanos = -1;

        @Override
        public double seconds() {
            long ref = pausedAtNanos >= 0 ? pausedAtNanos : System.nanoTime();
            return (ref - baseNanos) / 1_000_000_000.0;
        }

        public void setPaused(boolean paused) {
            if (paused && pausedAtNanos < 0) {
                pausedAtNanos = System.nanoTime();
            } else if (!paused && pausedAtNanos >= 0) {
                baseNanos += System.nanoTime() - pausedAtNanos;
                pausedAtNanos = -1;
            }
        }
    }
}
