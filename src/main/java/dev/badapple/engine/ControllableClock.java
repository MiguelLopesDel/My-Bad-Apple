package dev.badapple.engine;

/** A wall-clock timeline with pause, seek and speed, clamped to [0, duration]. */
final class ControllableClock {

    private final double duration;
    private double base;
    private long anchorNanos = System.nanoTime();
    private double speed = 1.0;
    private boolean paused;

    ControllableClock(double duration) {
        this.duration = duration;
    }

    double seconds() {
        double v = paused ? base : base + (System.nanoTime() - anchorNanos) / 1_000_000_000.0 * speed;
        return clamp(v);
    }

    void setBase(double seconds) {
        base = clamp(seconds);
        anchorNanos = System.nanoTime();
    }

    void setPaused(boolean p) {
        base = seconds();
        anchorNanos = System.nanoTime();
        paused = p;
    }

    void seek(double deltaSeconds) {
        setBase(seconds() + deltaSeconds);
    }

    void setSpeed(double newSpeed) {
        base = seconds();
        anchorNanos = System.nanoTime();
        speed = newSpeed;
    }

    double speed() {
        return speed;
    }

    private double clamp(double v) {
        return v < 0 ? 0 : Math.min(v, duration);
    }
}
