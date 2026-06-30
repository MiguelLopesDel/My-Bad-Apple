package dev.badapple.debug;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;

import java.nio.file.Path;

/**
 * Headless sanity tool: decodes a frame from the real asset, downscales it and prints it
 * as ASCII so the silhouette can be eyeballed without a TTY. Not shipped in the jar.
 *
 * <p>Usage: {@code Preview [frameIndex] [cols] [rows] [assetPath]}
 */
public final class Preview {

    private static final String RAMP = " .:-=+*#%@";

    public static void main(String[] args) throws Exception {
        int frameIndex = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        int cols = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        int rows = args.length > 2 ? Integer.parseInt(args[2]) : 45;
        Path assetPath = Path.of(args.length > 3 ? args[3] : "src/main/resources/badapple/frames.bin");

        AssetReader asset = AssetReader.load(assetPath);
        System.out.printf("asset: %dx%d, %d fps, %d frames%n",
                asset.width(), asset.height(), asset.fps(), asset.frameCount());

        Frame frame = asset.frameAt(Math.min(frameIndex, asset.frameCount() - 1));
        GrayGrid grid = new GrayGrid(cols, rows);
        Downscaler.downscale(frame, grid);

        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float lum = grid.get(x, y);
                int idx = Math.min(RAMP.length() - 1, Math.round(lum * (RAMP.length() - 1)));
                sb.append(RAMP.charAt(idx));
            }
            sb.append('\n');
        }
        System.out.print(sb);
    }

    private Preview() {
    }
}
