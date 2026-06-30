package dev.badapple.asset;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Unsigned LEB128 variable-length integers. Small run lengths cost a single byte,
 * which keeps the RLE stream compact.
 */
public final class Varint {

    private Varint() {
    }

    /** Writes {@code value} (treated as unsigned) to the stream. */
    public static void writeUnsigned(OutputStream out, int value) throws IOException {
        int v = value;
        do {
            int b = v & 0x7F;
            v >>>= 7;
            if (v != 0) {
                b |= 0x80;
            }
            out.write(b);
        } while (v != 0);
    }

    /** Reads an unsigned LEB128 int from {@code data} starting at {@code pos[0]}, advancing it. */
    public static int readUnsigned(byte[] data, int[] pos) {
        int result = 0;
        int shift = 0;
        int p = pos[0];
        while (true) {
            int b = data[p++] & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        pos[0] = p;
        return result;
    }
}
