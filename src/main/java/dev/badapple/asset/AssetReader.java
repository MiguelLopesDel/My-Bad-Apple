package dev.badapple.asset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a {@code frames.bin} asset fully into memory and decodes frames on demand.
 *
 * <p>A one-pass scan on load records, for every frame, its byte offset, payload bounds,
 * and type. Sequential playback ({@link #frameAt(int)} with increasing indices) only
 * applies the next delta; seeking jumps to the nearest preceding keyframe and replays
 * deltas forward. RAM stays at the compressed asset size plus two working frames.
 */
public final class AssetReader {

    private final int width;
    private final int height;
    private final int fps;
    private final int frameCount;
    private final byte[] data;

    private final int[] payloadOffset;
    private final int[] payloadLength;
    private final boolean[] keyframe;

    private final Frame currentShape;
    private final Frame deltaScratch;
    private int decodedIndex = -1;

    private AssetReader(int width, int height, int fps, int frameCount, byte[] data,
                        int[] payloadOffset, int[] payloadLength, boolean[] keyframe) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.frameCount = frameCount;
        this.data = data;
        this.payloadOffset = payloadOffset;
        this.payloadLength = payloadLength;
        this.keyframe = keyframe;
        this.currentShape = new Frame(width, height);
        this.deltaScratch = new Frame(width, height);
    }

    public static AssetReader load(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    public static AssetReader load(InputStream in) throws IOException {
        return parse(in.readAllBytes());
    }

    private static AssetReader parse(byte[] data) throws IOException {
        for (int i = 0; i < AssetFormat.MAGIC.length; i++) {
            if (data[i] != AssetFormat.MAGIC[i]) {
                throw new IOException("Not a Bad Apple asset (bad magic)");
            }
        }
        int p = AssetFormat.MAGIC.length;
        int version = data[p++] & 0xFF;
        if (version != AssetFormat.VERSION) {
            throw new IOException("Unsupported asset version: " + version);
        }
        int width = readU16(data, p);
        p += 2;
        int height = readU16(data, p);
        p += 2;
        int fps = readU16(data, p);
        p += 2;
        int frameCount = readI32(data, p);
        p += 4;
        p += 2; // keyInterval (informational; index is rebuilt here)
        p += 1; // flags

        int[] offsets = new int[frameCount];
        int[] lengths = new int[frameCount];
        boolean[] isKey = new boolean[frameCount];
        int[] pos = {p};
        for (int i = 0; i < frameCount; i++) {
            int type = data[pos[0]++] & 0xFF;
            int len = Varint.readUnsigned(data, pos);
            offsets[i] = pos[0];
            lengths[i] = len;
            isKey[i] = (type == AssetFormat.TYPE_KEYFRAME);
            pos[0] += len;
        }
        return new AssetReader(width, height, fps, frameCount, data, offsets, lengths, isKey);
    }

    /**
     * Returns the shape at {@code index}. The returned frame is owned and reused by the
     * reader, so callers must consume it before requesting another.
     */
    public Frame frameAt(int index) {
        if (index < 0 || index >= frameCount) {
            throw new IndexOutOfBoundsException("frame " + index + " of " + frameCount);
        }
        if (index == decodedIndex) {
            return currentShape;
        }
        // Sequential fast path: advancing by one delta from the current frame.
        if (index == decodedIndex + 1 && !keyframe[index]) {
            applyDelta(index);
            decodedIndex = index;
            return currentShape;
        }
        // Otherwise jump to the nearest preceding keyframe and replay forward.
        int start = index;
        while (start > 0 && !keyframe[start]) {
            start--;
        }
        FrameCodec.decode(data, payloadOffset[start], payloadLength[start], currentShape);
        for (int i = start + 1; i <= index; i++) {
            applyDelta(i);
        }
        decodedIndex = index;
        return currentShape;
    }

    private void applyDelta(int index) {
        FrameCodec.decode(data, payloadOffset[index], payloadLength[index], deltaScratch);
        currentShape.xorInto(deltaScratch, currentShape);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int fps() {
        return fps;
    }

    public int frameCount() {
        return frameCount;
    }

    private static int readU16(byte[] d, int p) {
        return ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
    }

    private static int readI32(byte[] d, int p) {
        return ((d[p] & 0xFF) << 24) | ((d[p + 1] & 0xFF) << 16)
                | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
    }
}
