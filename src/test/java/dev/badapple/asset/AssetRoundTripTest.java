package dev.badapple.asset;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetRoundTripTest {

    @Test
    void writesAndReadsBackEveryFrame(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        int w = 48, h = 36, count = 95; // spans several keyframe intervals
        List<Frame> originals = makeFrames(w, h, count, new Random(99));

        Path bin = tmp.resolve("frames.bin");
        AssetWriter writer = new AssetWriter(bin, w, h, 30, AssetFormat.DEFAULT_KEY_INTERVAL);
        for (Frame f : originals) {
            writer.add(f);
        }
        writer.finish();

        AssetReader reader = AssetReader.load(bin);
        assertEquals(count, reader.frameCount());
        assertEquals(w, reader.width());
        assertEquals(h, reader.height());

        // Sequential playback path.
        for (int i = 0; i < count; i++) {
            assertEquals(originals.get(i), reader.frameAt(i), "sequential frame " + i);
        }
        // Random-access seek path (jumps across keyframes).
        int[] order = {90, 3, 60, 31, 0, 94, 45, 29, 30};
        for (int i : order) {
            assertEquals(originals.get(i), reader.frameAt(i), "seek frame " + i);
        }
    }

    /** Frames that evolve gradually, so deltas are sparse like the real video. */
    private static List<Frame> makeFrames(int w, int h, int count, Random rnd) {
        List<Frame> frames = new ArrayList<>();
        Frame state = new Frame(w, h);
        for (int i = 0; i < w * h; i++) {
            state.set(i, rnd.nextInt(5) == 0);
        }
        for (int f = 0; f < count; f++) {
            for (int flip = 0; flip < 20; flip++) {
                int idx = rnd.nextInt(w * h);
                state.set(idx, !state.get(idx));
            }
            Frame snapshot = new Frame(w, h);
            snapshot.copyFrom(state);
            frames.add(snapshot);
        }
        return frames;
    }
}
