package dev.badapple.render.backends;

import dev.badapple.render.Ansi;

import java.util.Base64;

/**
 * Renders each frame as an inline image using iTerm2's OSC 1337 protocol. The PNG is base64'd
 * and sized to the terminal's cell grid ({@code width}/{@code height} in cells), with aspect
 * preservation off so it exactly fills the laid-out area.
 */
public final class ITermRenderer extends ImageRenderer {

    private static final char BEL = 7;

    @Override
    protected void encode(int[] argb, int width, int height, StringBuilder out) {
        byte[] png = PngImage.encode(argb, width, height);
        String b64 = Base64.getEncoder().encodeToString(png);

        out.append(Ansi.ESC).append("]1337;File=inline=1")
                .append(";size=").append(png.length)
                .append(";width=").append(cols)
                .append(";height=").append(rows)
                .append(";preserveAspectRatio=0:")
                .append(b64)
                .append(BEL);
    }
}
