package dev.badapple.engine;

/**
 * The playback time source the player reads each frame.
 *
 * <p>With audio present it tracks the audio line position exactly (audio is master, zero
 * drift) and pause simply freezes the line. Seeking or changing speed can't be followed by a
 * streamed MP3, so the first such action "detaches": the timeline switches to a controllable
 * wall clock seeded at the current position and the audio is muted. Without audio the timeline
 * is detached from the start, so every control works fully.
 */
public final class Timeline {

    private static final double MIN_SPEED = 0.25;
    private static final double MAX_SPEED = 4.0;

    private final AudioPlayer audio;
    private final ControllableClock clock;
    private boolean detached;
    private boolean paused;
    private double speed = 1.0;

    public Timeline(AudioPlayer audio, double duration) {
        this.audio = audio;
        this.clock = new ControllableClock(duration);
        this.detached = (audio == null);
    }

    public double seconds() {
        return detached ? clock.seconds() : audio.positionSeconds();
    }

    public void togglePause() {
        paused = !paused;
        if (detached) {
            clock.setPaused(paused);
        } else {
            audio.setPaused(paused);
        }
    }

    public void seek(double deltaSeconds) {
        ensureDetached();
        clock.seek(deltaSeconds);
    }

    public void changeSpeed(double factor) {
        ensureDetached();
        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed * factor));
        clock.setSpeed(speed);
    }

    public boolean isPaused() {
        return paused;
    }

    public double speed() {
        return speed;
    }

    public boolean isDetached() {
        return detached;
    }

    /** Switch from audio-master to the controllable clock, muting audio (which can't follow). */
    private void ensureDetached() {
        if (detached) {
            return;
        }
        clock.setBase(audio.positionSeconds());
        audio.setPaused(true);
        detached = true;
        paused = false;
    }
}
