package dev.badapple.debug;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.render.Ansi;
import dev.badapple.render.Colorizer;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.LineRenderer;
import dev.badapple.render.Renderer;
import dev.badapple.render.backends.HalfBlockRenderer;
import dev.badapple.render.backends.SixelRenderer;
import dev.badapple.render.colorizers.Colorizers;

import java.nio.file.Path;

/**
 * Headless per-frame benchmark: times decode + downscale + render-into-StringBuilder for the
 * real asset, with no terminal involved. This isolates CPU render cost from terminal write
 * cost, so a low number here means the renderer is the bottleneck and a high number means the
 * terminal (or transport) is.
 *
 * <p>Usage: {@code Bench [mode] [cols] [rows] [frames]} where mode is one of
 * {@code halfblock-mono | halfblock-gradient | sixel}.
 */
public final class Bench {

    public static void main(String[] argv) throws Exception {
        String mode = argv.length > 0 ? argv[0] : "halfblock-mono";
        int cols = argv.length > 1 ? Integer.parseInt(argv[1]) : 190;
        int rows = argv.length > 2 ? Integer.parseInt(argv[2]) : 50;
        int frames = argv.length > 3 ? Integer.parseInt(argv[3]) : 600;
        Path assetPath = Path.of("src/main/resources/badapple/frames.bin");

        AssetReader asset = AssetReader.load(assetPath);

        Renderer renderer;
        Colorizer colorizer;
        switch (mode) {
            case "halfblock-gradient" -> {
                renderer = new HalfBlockRenderer(ColorDepth.TRUECOLOR);
                colorizer = Colorizers.create("gradient", "aurora");
            }
            case "sixel" -> {
                renderer = new SixelRenderer();
                colorizer = Colorizers.create("gradient", "aurora");
            }
            default -> {
                renderer = new HalfBlockRenderer(ColorDepth.TRUECOLOR);
                colorizer = Colorizers.create("mono", null);
            }
        }

        Renderer.GridSize gs = renderer.gridSize(cols, rows, 10, 22);
        GrayGrid grid = new GrayGrid(gs.width(), gs.height());
        StringBuilder sb = new StringBuilder(gs.width() * gs.height() * 4);
        LineRenderer lr = renderer instanceof LineRenderer x ? x : null;
        String[] cur = lr == null ? null : new String[lr.lineCount(grid)];
        String[] prev = lr == null ? null : new String[lr.lineCount(grid)];

        // Warm up the JIT and the asset's sequential fast path.
        for (int i = 0; i < 120; i++) {
            Frame f = asset.frameAt(i % asset.frameCount());
            Downscaler.downscale(f, grid);
            render(renderer, lr, grid, colorizer, (double) i / frames, sb, cur, prev);
        }

        long fullBytes = 0;
        long diffedBytes = 0;
        long worstNs = 0;
        long start = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            long t0 = System.nanoTime();
            Frame f = asset.frameAt(i % asset.frameCount());
            Downscaler.downscale(f, grid);
            diffedBytes += render(renderer, lr, grid, colorizer, (double) i / frames, sb, cur, prev);
            long dt = System.nanoTime() - t0;
            worstNs = Math.max(worstNs, dt);
            if (lr != null && cur != null) {
                for (String s : cur) {
                    fullBytes += s == null ? 0 : s.length();
                }
            } else {
                fullBytes += sb.length();
            }
        }
        long elapsed = System.nanoTime() - start;

        double seconds = elapsed / 1e9;
        double fps = frames / seconds;
        System.out.printf("mode=%s grid=%dx%d frames=%d%n", mode, gs.width(), gs.height(), frames);
        System.out.printf("  render-only: %.1f fps  (%.2f ms/frame avg, %.2f ms worst)%n",
                fps, seconds / frames * 1000, worstNs / 1e6);
        System.out.printf("  output full: %.1f KB/frame  ·  after line-diff: %.1f KB/frame%n",
                fullBytes / (double) frames / 1024, diffedBytes / (double) frames / 1024);
    }

    /** Renders a frame, returning the number of chars that would actually be written (post-diff). */
    private static long render(Renderer renderer, LineRenderer lr, GrayGrid grid, Colorizer col,
                               double t, StringBuilder sb, String[] cur, String[] prev) {
        sb.setLength(0);
        if (lr == null) {
            renderer.render(grid, col, t, sb);
            return sb.length();
        }
        lr.renderLines(grid, col, t, cur);
        long written = 0;
        for (int r = 0; r < cur.length; r++) {
            if (!cur[r].equals(prev[r])) {
                Ansi.moveTo(sb, r + 1, 1);
                sb.append(cur[r]);
                written += sb.length();
                sb.setLength(0);
                prev[r] = cur[r];
            }
        }
        return written;
    }

    private Bench() {
    }
}
