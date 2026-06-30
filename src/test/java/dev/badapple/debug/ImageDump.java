package dev.badapple.debug;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.render.Colorizer;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.Renderer;
import dev.badapple.render.backends.KittyRenderer;
import dev.badapple.render.colorizers.Colorizers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Headless check for the image backends: runs the kitty renderer on a frame, extracts the
 * base64 PNG it transmits, and writes it out so the actual image the terminal would show can
 * be inspected. Proves the compose → PNG → protocol path end to end.
 *
 * <p>Usage: {@code ImageDump <outPng> [frame] [color] [palette] [assetPath]}
 */
public final class ImageDump {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "image-dump.png");
        int frameIndex = args.length > 1 ? Integer.parseInt(args[1]) : 3000;
        String color = args.length > 2 ? args[2] : "lut";
        String palette = args.length > 3 ? args[3] : "fire";
        Path assetPath = Path.of(args.length > 4 ? args[4] : "src/main/resources/badapple/frames.bin");

        AssetReader asset = AssetReader.load(assetPath);
        int idx = Math.min(frameIndex, asset.frameCount() - 1);
        Frame frame = asset.frameAt(idx);
        Colorizer colorizer = Colorizers.create(color, palette);

        Renderer renderer = new KittyRenderer();
        Renderer.GridSize gs = renderer.gridSize(120, 50, 8, 16);
        GrayGrid grid = new GrayGrid(gs.width(), gs.height());
        Downscaler.downscale(frame, grid);

        StringBuilder sb = new StringBuilder();
        renderer.render(grid, colorizer, (double) idx / asset.frameCount(), sb);

        byte[] png = extractKittyPng(sb.toString());
        Files.write(out, png);
        System.out.printf("wrote %s (%d bytes PNG, grid %dx%d)%n", out, png.length, gs.width(), gs.height());
    }

    /** Concatenates the base64 payloads from every kitty graphics chunk and decodes the PNG. */
    private static byte[] extractKittyPng(String s) {
        StringBuilder b64 = new StringBuilder();
        int i = 0;
        String open = "" + (char) 27 + "_G";
        String close = "" + (char) 27 + "\\";
        while (true) {
            int g = s.indexOf(open, i);
            if (g < 0) {
                break;
            }
            int semi = s.indexOf(';', g);
            int end = s.indexOf(close, semi);
            if (semi < 0 || end < 0) {
                break;
            }
            b64.append(s, semi + 1, end);
            i = end + close.length();
        }
        return Base64.getDecoder().decode(b64.toString());
    }

    private ImageDump() {
    }
}
