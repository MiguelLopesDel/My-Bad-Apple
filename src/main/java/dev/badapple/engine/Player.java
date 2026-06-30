package dev.badapple.engine;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.render.Colorizer;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.Renderer;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.PrintWriter;

/**
 * Drives playback: sizes the image to the terminal, then renders frames on a fixed clock,
 * dropping frames when it falls behind. Audio sync replaces the fixed clock in a later phase.
 */
public final class Player {

    private static final String ESC = "";
    private static final String HIDE_CURSOR = ESC + "[?25l";
    private static final String SHOW_CURSOR = ESC + "[?25h";
    private static final String ALT_SCREEN_ON = ESC + "[?1049h";
    private static final String ALT_SCREEN_OFF = ESC + "[?1049l";
    private static final String CURSOR_HOME = ESC + "[H";

    private final AssetReader asset;
    private final Renderer renderer;
    private final Colorizer colorizer;

    public Player(AssetReader asset, Renderer renderer, Colorizer colorizer) {
        this.asset = asset;
        this.renderer = renderer;
        this.colorizer = colorizer;
    }

    public void play() throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        PrintWriter w = terminal.writer();
        Thread restore = new Thread(() -> {
            w.print(SHOW_CURSOR);
            w.print(ALT_SCREEN_OFF);
            w.flush();
        });
        Runtime.getRuntime().addShutdownHook(restore);
        try {
            w.print(ALT_SCREEN_ON);
            w.print(HIDE_CURSOR);
            w.flush();
            runLoop(terminal, w);
        } finally {
            Runtime.getRuntime().removeShutdownHook(restore);
            w.print(SHOW_CURSOR);
            w.print(ALT_SCREEN_OFF);
            w.flush();
            terminal.close();
        }
    }

    private void runLoop(Terminal terminal, PrintWriter w) throws InterruptedException {
        int cols = Math.max(1, terminal.getWidth());
        int rows = Math.max(1, terminal.getHeight());
        int subRows = rows * 2;

        // Fit the source aspect ratio inside the terminal, letterboxing the rest.
        double aspect = (double) asset.width() / asset.height();
        int imgW;
        int imgH;
        if ((double) cols / subRows > aspect) {
            imgH = subRows;
            imgW = Math.max(1, (int) Math.round(subRows * aspect));
        } else {
            imgW = cols;
            imgH = Math.max(1, (int) Math.round(cols / aspect));
        }
        imgW = Math.min(imgW, cols);
        imgH = Math.min(imgH, subRows);
        int offsetX = (cols - imgW) / 2;
        int offsetY = (subRows - imgH) / 2;

        GrayGrid image = new GrayGrid(imgW, imgH);
        GrayGrid screen = new GrayGrid(cols, subRows);
        StringBuilder sb = new StringBuilder(cols * subRows * 4);

        int frameCount = asset.frameCount();
        double fps = asset.fps() > 0 ? asset.fps() : 30.0;
        double frameNs = 1_000_000_000.0 / fps;
        long startNs = System.nanoTime();

        for (int i = 0; i < frameCount; i++) {
            long target = startNs + (long) (i * frameNs);
            long now = System.nanoTime();
            // Drop frames we're already more than one frame late for (except the last).
            if (now - target > frameNs && i < frameCount - 1) {
                continue;
            }
            long wait = target - System.nanoTime();
            if (wait > 0) {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            }

            Frame frame = asset.frameAt(i);
            Downscaler.downscale(frame, image);
            screen.fill(0f);
            for (int y = 0; y < imgH; y++) {
                for (int x = 0; x < imgW; x++) {
                    screen.set(offsetX + x, offsetY + y, image.get(x, y));
                }
            }

            sb.setLength(0);
            renderer.render(screen, colorizer, (double) i / frameCount, sb);
            w.print(CURSOR_HOME);
            w.print(sb);
            w.flush();
        }
    }
}
