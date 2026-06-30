package dev.badapple.debug;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.Renderer;
import dev.badapple.render.backends.QuadrantRenderer;
import dev.badapple.render.colorizers.Colorizers;

import java.nio.file.Path;

/**
 * Headless sanity tool: lays out and renders one frame exactly like the player (aspect-correct
 * letterbox into a cols x rows text area with the given cell pixel size), then prints the
 * quadrant glyphs with ANSI stripped, so proportions and sharpness can be eyeballed.
 *
 * <p>Usage: {@code QuadDump [frame] [cols] [rows] [cellW] [cellH]}
 */
public final class QuadDump {

    public static void main(String[] args) throws Exception {
        int frame = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        int cols = args.length > 1 ? Integer.parseInt(args[1]) : 80;
        int rows = args.length > 2 ? Integer.parseInt(args[2]) : 24;
        int cellW = args.length > 3 ? Integer.parseInt(args[3]) : 10;
        int cellH = args.length > 4 ? Integer.parseInt(args[4]) : 22;
        AssetReader asset = AssetReader.load(Path.of("src/main/resources/badapple/frames.bin"));

        QuadrantRenderer renderer = new QuadrantRenderer(ColorDepth.TRUECOLOR);
        Renderer.GridSize gs = renderer.gridSize(cols, rows, cellW, cellH);
        int gridW = gs.width();
        int gridH = gs.height();

        double hUnitPx = (double) cols * cellW / gridW;
        double vUnitPx = (double) rows * cellH / gridH;
        double aspect = (double) asset.width() / asset.height() * (vUnitPx / hUnitPx);
        int imgW;
        int imgH;
        if ((double) gridW / gridH > aspect) {
            imgH = gridH;
            imgW = Math.max(1, (int) Math.round(gridH * aspect));
        } else {
            imgW = gridW;
            imgH = Math.max(1, (int) Math.round(gridW / aspect));
        }
        imgW = Math.min(imgW, gridW);
        imgH = Math.min(imgH, gridH);

        GrayGrid image = new GrayGrid(imgW, imgH);
        GrayGrid screen = new GrayGrid(gridW, gridH);
        Frame f = asset.frameAt(Math.min(frame, asset.frameCount() - 1));
        Downscaler.downscale(f, image);
        int offX = (gridW - imgW) / 2;
        int offY = (gridH - imgH) / 2;
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                screen.set(offX + x, offY + y, image.get(x, y));
            }
        }

        String[] lines = new String[renderer.lineCount(screen)];
        renderer.renderLines(screen, Colorizers.create("mono", null), 0.0, lines);
        System.out.printf("grid %dx%d  image %dx%d  aspect %.2f%n", gridW, gridH, imgW, imgH, aspect);
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
