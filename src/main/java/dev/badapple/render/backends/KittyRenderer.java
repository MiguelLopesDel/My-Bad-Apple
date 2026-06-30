package dev.badapple.render.backends;

import dev.badapple.render.Ansi;

import java.util.Base64;

/**
 * Renders each frame via the kitty graphics protocol: a PNG transmitted and displayed in one
 * shot, scaled to the terminal's cell grid. Payload is base64 and chunked at 4096 bytes; a
 * fixed image id is reused so successive frames replace one another, and {@code q=2} silences
 * the terminal's acknowledgements (we never read them back).
 */
public final class KittyRenderer extends ImageRenderer {

    private static final int CHUNK = 4096;

    @Override
    protected void encode(int[] argb, int width, int height, StringBuilder out) {
        byte[] png = PngImage.encode(argb, width, height);
        String b64 = Base64.getEncoder().encodeToString(png);

        for (int offset = 0; offset < b64.length(); offset += CHUNK) {
            int end = Math.min(b64.length(), offset + CHUNK);
            boolean first = offset == 0;
            boolean last = end == b64.length();

            out.append(Ansi.ESC).append("_G");
            if (first) {
                out.append("a=T,f=100,i=1,q=2,c=").append(cols).append(",r=").append(rows).append(',');
            }
            out.append("m=").append(last ? 0 : 1).append(';');
            out.append(b64, offset, end);
            out.append(Ansi.ESC).append('\\');
        }
    }
}
