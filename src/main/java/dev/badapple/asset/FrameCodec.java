package dev.badapple.asset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Run-length codec for 1-bit frames. The bit sequence (row-major) is encoded as
 * alternating run lengths, always starting with a "dark" run (which may be zero).
 *
 * <p>Because Bad Apple is mostly large solid regions, and delta frames (the XOR of
 * consecutive frames) are almost entirely zeros, the runs get very long and the
 * stream compresses to a small fraction of the raw bitmap.
 */
public final class FrameCodec {

    private FrameCodec() {
    }

    /** RLE-encodes the frame's raw bits into a compact byte array. */
    public static byte[] encode(Frame frame) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n = frame.bitCount();
        boolean current = false;
        int i = 0;
        try {
            while (i < n) {
                int run = 0;
                while (i < n && frame.get(i) == current) {
                    run++;
                    i++;
                }
                Varint.writeUnsigned(out, run);
                current = !current;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // ByteArrayOutputStream never throws
        }
        return out.toByteArray();
    }

    /** Decodes an RLE payload into {@code out}, which is cleared first. */
    public static void decode(byte[] data, int offset, int length, Frame out) {
        out.clear();
        int n = out.bitCount();
        int[] pos = {offset};
        int end = offset + length;
        boolean current = false;
        int i = 0;
        while (i < n && pos[0] < end) {
            int run = Varint.readUnsigned(data, pos);
            if (current) {
                for (int k = 0; k < run; k++) {
                    out.set(i + k, true);
                }
            }
            i += run;
            current = !current;
        }
    }
}
