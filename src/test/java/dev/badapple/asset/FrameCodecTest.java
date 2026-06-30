package dev.badapple.asset;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FrameCodecTest {

    @Test
    void encodesAndDecodesAllDark() {
        Frame f = new Frame(13, 7);
        roundTrip(f);
    }

    @Test
    void encodesAndDecodesAllLight() {
        Frame f = new Frame(13, 7);
        for (int i = 0; i < f.bitCount(); i++) {
            f.set(i, true);
        }
        roundTrip(f);
    }

    @Test
    void encodesAndDecodesFrameStartingLight() {
        // First run is "dark" with length 0, exercising the zero-length leading run.
        Frame f = new Frame(8, 1);
        f.set(0, true);
        f.set(1, true);
        roundTrip(f);
    }

    @Test
    void encodesAndDecodesRandomFrames() {
        Random rnd = new Random(42);
        for (int trial = 0; trial < 50; trial++) {
            Frame f = new Frame(64, 48);
            for (int i = 0; i < f.bitCount(); i++) {
                f.set(i, rnd.nextInt(4) == 0); // sparse, like real frames
            }
            roundTrip(f);
        }
    }

    @Test
    void deltaRoundTripReconstructsFrame() {
        Random rnd = new Random(7);
        Frame prev = randomFrame(64, 48, rnd);
        Frame cur = randomFrame(64, 48, rnd);
        assertNotEquals(prev, cur);

        Frame delta = new Frame(64, 48);
        cur.xorInto(prev, delta);
        byte[] encoded = FrameCodec.encode(delta);

        Frame decodedDelta = new Frame(64, 48);
        FrameCodec.decode(encoded, 0, encoded.length, decodedDelta);

        Frame reconstructed = new Frame(64, 48);
        prev.xorInto(decodedDelta, reconstructed);
        assertEquals(cur, reconstructed);
    }

    private static Frame randomFrame(int w, int h, Random rnd) {
        Frame f = new Frame(w, h);
        for (int i = 0; i < f.bitCount(); i++) {
            f.set(i, rnd.nextBoolean());
        }
        return f;
    }

    private static void roundTrip(Frame f) {
        byte[] encoded = FrameCodec.encode(f);
        Frame decoded = new Frame(f.width, f.height);
        FrameCodec.decode(encoded, 0, encoded.length, decoded);
        assertEquals(f, decoded);
    }
}
