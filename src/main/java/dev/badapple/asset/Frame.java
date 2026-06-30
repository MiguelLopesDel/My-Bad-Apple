package dev.badapple.asset;

import java.util.Arrays;

/**
 * A single 1-bit frame: the pure shape of Bad Apple, with no color.
 *
 * <p>Pixels are stored row-major and packed into a {@code long[]}. A set bit means
 * "light" (the source pixel was at or above the binarization threshold). Color is
 * never stored here — it is computed at render time from the downscaled luminance.
 */
public final class Frame {

    public final int width;
    public final int height;
    private final long[] words;

    public Frame(int width, int height) {
        this.width = width;
        this.height = height;
        this.words = new long[(width * height + 63) >>> 6];
    }

    /** Total number of pixels (bits). */
    public int bitCount() {
        return width * height;
    }

    public boolean get(int index) {
        return (words[index >>> 6] & (1L << (index & 63))) != 0;
    }

    public void set(int index, boolean value) {
        int w = index >>> 6;
        long mask = 1L << (index & 63);
        if (value) {
            words[w] |= mask;
        } else {
            words[w] &= ~mask;
        }
    }

    /** Clears every bit to "dark". */
    public void clear() {
        Arrays.fill(words, 0L);
    }

    /** Copies {@code source} into this frame; both must have identical dimensions. */
    public void copyFrom(Frame source) {
        System.arraycopy(source.words, 0, this.words, 0, this.words.length);
    }

    /** Writes {@code this XOR other} into {@code out}. Used for delta frames. */
    public void xorInto(Frame other, Frame out) {
        for (int i = 0; i < words.length; i++) {
            out.words[i] = this.words[i] ^ other.words[i];
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Frame other)) {
            return false;
        }
        return width == other.width && height == other.height && Arrays.equals(words, other.words);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(words);
    }
}
