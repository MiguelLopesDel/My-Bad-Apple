package dev.badapple.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FpsMeterTest {

    @Test
    void reportsZeroWithFewerThanTwoSamples() {
        FpsMeter meter = new FpsMeter();
        assertEquals(0.0, meter.fps(0), 1e-9, "no samples");
        meter.record(0);
        assertEquals(0.0, meter.fps(1_000_000), 1e-9, "single sample");
    }

    @Test
    void measuresSteadyRate() {
        FpsMeter meter = new FpsMeter();
        long step = 1_000_000_000L / 30; // 30 fps
        long t = 0;
        for (int i = 0; i < 30; i++) {
            meter.record(t);
            t += step;
        }
        assertEquals(30.0, meter.fps(t), 0.5, "30 evenly spaced frames read as ~30 fps");
    }

    @Test
    void slowPresentationReadsLow() {
        FpsMeter meter = new FpsMeter();
        long step = 1_000_000_000L / 25; // only 25 frames make it out per second
        long t = 0;
        for (int i = 0; i < 25; i++) {
            meter.record(t);
            t += step;
        }
        double fps = meter.fps(t);
        assertTrue(fps < 27.0 && fps > 23.0, "reports the true ~25 fps, got " + fps);
    }

    @Test
    void dropsSamplesOutsideTheWindow() {
        FpsMeter meter = new FpsMeter();
        meter.record(0);
        meter.record(10_000_000); // 10ms in
        // Two seconds later, the old samples are outside the 1s window.
        assertEquals(0.0, meter.fps(2_000_000_000L), 1e-9, "stale samples ignored");
    }
}
