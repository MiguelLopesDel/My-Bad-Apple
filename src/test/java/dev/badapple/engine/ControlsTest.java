package dev.badapple.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlsTest {

    @Test
    void clockClampsToBounds() {
        ControllableClock clock = new ControllableClock(10.0);
        clock.setBase(20.0);
        assertEquals(10.0, clock.seconds(), 0.05, "clamped to duration");
        clock.setBase(-5.0);
        assertEquals(0.0, clock.seconds(), 0.05, "clamped to zero");
    }

    @Test
    void pausedClockDoesNotAdvance() throws Exception {
        ControllableClock clock = new ControllableClock(100.0);
        clock.setBase(3.0);
        clock.setPaused(true);
        Thread.sleep(40);
        assertEquals(3.0, clock.seconds(), 0.001, "frozen while paused");
    }

    @Test
    void seekMovesAndClamps() {
        ControllableClock clock = new ControllableClock(10.0);
        clock.setBase(8.0);
        clock.setPaused(true);
        clock.seek(5.0);
        assertEquals(10.0, clock.seconds(), 0.05, "seek past end clamps");
        clock.seek(-50.0);
        assertEquals(0.0, clock.seconds(), 0.05, "seek before start clamps");
    }

    @Test
    void timelineWithoutAudioIsDetachedAndControllable() {
        Timeline tl = new Timeline(null, 100.0);
        assertTrue(tl.isDetached(), "no audio: detached from the start");
        assertFalse(tl.isPaused());
        tl.togglePause();
        assertTrue(tl.isPaused());
        tl.togglePause();
        assertFalse(tl.isPaused());
    }

    @Test
    void speedClampsToRange() {
        Timeline tl = new Timeline(null, 100.0);
        for (int i = 0; i < 20; i++) {
            tl.changeSpeed(1.25);
        }
        assertTrue(tl.speed() <= 4.0 + 1e-9, "speed capped at 4x");
        for (int i = 0; i < 40; i++) {
            tl.changeSpeed(0.8);
        }
        assertTrue(tl.speed() >= 0.25 - 1e-9, "speed floored at 0.25x");
    }
}
