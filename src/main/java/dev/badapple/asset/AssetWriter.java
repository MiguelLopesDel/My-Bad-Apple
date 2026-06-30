package dev.badapple.asset;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Streams 1-bit frames into the {@code frames.bin} format, emitting a keyframe every
 * {@link #keyInterval} frames and XOR delta frames in between.
 *
 * <p>The compressed frame stream (a few MB) is buffered in memory so the final frame
 * count is known before the header is written. {@link #finish()} then writes header +
 * body to the target file in one pass.
 */
public final class AssetWriter {

    private final Path target;
    private final int width;
    private final int height;
    private final int fps;
    private final int keyInterval;
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    private Frame previous;
    private Frame deltaScratch;
    private int frameCount;

    public AssetWriter(Path target, int width, int height, int fps, int keyInterval) {
        this.target = target;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.keyInterval = keyInterval;
    }

    /** Appends one frame, choosing keyframe vs delta based on position. */
    public void add(Frame frame) throws IOException {
        boolean keyframe = (frameCount % keyInterval == 0) || previous == null;
        if (keyframe) {
            byte[] payload = FrameCodec.encode(frame);
            body.write(AssetFormat.TYPE_KEYFRAME);
            Varint.writeUnsigned(body, payload.length);
            body.write(payload);
        } else {
            if (deltaScratch == null) {
                deltaScratch = new Frame(width, height);
            }
            frame.xorInto(previous, deltaScratch);
            byte[] payload = FrameCodec.encode(deltaScratch);
            body.write(AssetFormat.TYPE_DELTA);
            Varint.writeUnsigned(body, payload.length);
            body.write(payload);
        }
        if (previous == null) {
            previous = new Frame(width, height);
        }
        previous.copyFrom(frame);
        frameCount++;
    }

    /** Writes the complete asset (header + buffered frame stream) to the target file. */
    public void finish() throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(target));
             DataOutputStream out = new DataOutputStream(os)) {
            out.write(AssetFormat.MAGIC);
            out.writeByte(AssetFormat.VERSION);
            out.writeShort(width);
            out.writeShort(height);
            out.writeShort(fps);
            out.writeInt(frameCount);
            out.writeShort(keyInterval);
            out.writeByte(0); // flags
            body.writeTo(out);
        }
    }

    public int frameCount() {
        return frameCount;
    }
}
