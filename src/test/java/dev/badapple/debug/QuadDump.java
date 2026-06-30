package dev.badapple.debug;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.backends.QuadrantRenderer;
import dev.badapple.render.colorizers.Colorizers;

import java.nio.file.Path;

/**
 * Headless sanity tool: renders one frame with the quadrant renderer and prints the glyphs with
 * ANSI color stripped, so the silhouette and glyph variety can be eyeballed without a TTY.
 *
 * <p>Usage: {@code QuadDump [frame] [cols] [rows]}
 */
public final class QuadDump {

    public static void main(String[] args) throws Exception {
        int frame = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        int cols = args.length > 1 ? Integer.parseInt(args[1]) : 80;
        int rows = args.length > 2 ? Integer.parseInt(args[2]) : 24;
        AssetReader asset = AssetReader.load(Path.of("src/main/resources/badapple/frames.bin"));

        QuadrantRenderer renderer = new QuadrantRenderer(ColorDepth.TRUECOLOR);
        GrayGrid grid = new GrayGrid(cols * 2, rows * 2);
        Frame f = asset.frameAt(Math.min(frame, asset.frameCount() - 1));
        Downscaler.downscale(f, grid);

        String[] lines = new String[renderer.lineCount(grid)];
        renderer.renderLines(grid, Colorizers.create("mono", null), 0.0, lines);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(stripAnsi(line)).append('\n');
        }
        System.out.print(out);
    }

    /** Removes CSI escape sequences (ESC '[' ... final-byte) so only glyphs remain. */
    private static String stripAnsi(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 27) {
                i++; // skip '['
                while (i + 1 < s.length() && s.charAt(i + 1) != 'm') {
                    i++;
                }
                i++; // skip the 'm'
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private QuadDump() {
    }
}
