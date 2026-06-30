package dev.badapple.asset;

/**
 * On-disk layout of the embedded {@code frames.bin} asset.
 *
 * <pre>
 * Header (big-endian):
 *   magic        4 bytes  "BAPL"
 *   version      u8
 *   width        u16
 *   height       u16
 *   fps          u16
 *   frameCount   u32
 *   keyInterval  u16      keyframe every N frames
 *   flags        u8       reserved (0)
 * Frame stream (frameCount entries):
 *   type         u8       0 = keyframe, 1 = delta (XOR of previous frame)
 *   length       varint   payload byte length
 *   payload      RLE bits (FrameCodec)
 * </pre>
 *
 * No frame index is persisted: the reader builds one in a single pass on load,
 * since the whole asset comfortably fits in memory.
 */
public final class AssetFormat {

    public static final byte[] MAGIC = {'B', 'A', 'P', 'L'};
    public static final int VERSION = 1;
    public static final int DEFAULT_KEY_INTERVAL = 30;

    public static final int TYPE_KEYFRAME = 0;
    public static final int TYPE_DELTA = 1;

    /** Fixed header byte length up to the start of the frame stream. */
    public static final int HEADER_SIZE = 4 + 1 + 2 + 2 + 2 + 4 + 2 + 1;

    private AssetFormat() {
    }
}
