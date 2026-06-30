package dev.badapple.engine;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.cli.Args;
import dev.badapple.render.Ansi;
import dev.badapple.render.Colorizer;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.Renderer;
import dev.badapple.render.backends.RendererFactory;
import dev.badapple.terminal.CapabilityDetector;
import dev.badapple.terminal.TerminalCapabilities;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.InputStream;
import java.io.PrintWriter;

/**
 * Drives playback: detects the terminal, picks a renderer, then renders frames against a
 * {@link Timeline}. Keyboard input both paces the loop and handles controls (pause, seek,
 * speed, quit); terminal resizes are picked up live.
 */
public final class Player {

    private static final double SEEK_SECONDS = 5.0;
    private static final long INPUT_BUDGET_MS = 20;

    private final AssetReader asset;
    private final Colorizer colorizer;
    private final InputStream audioStream;
    private final Args args;

    public Player(AssetReader asset, Colorizer colorizer, InputStream audioStream, Args args) {
        this.asset = asset;
        this.colorizer = colorizer;
        this.audioStream = audioStream;
        this.args = args;
    }

    public void play() throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();

        TerminalCapabilities caps = CapabilityDetector.detect(terminal, !args.force);
        if (args.debug) {
            terminal.close();
            System.out.println(caps);
            return;
        }
        Renderer renderer = RendererFactory.create(caps, args);

        PrintWriter w = terminal.writer();
        Thread restore = new Thread(() -> {
            w.print(Ansi.SHOW_CURSOR);
            w.print(Ansi.ALT_SCREEN_OFF);
            w.flush();
        });
        Runtime.getRuntime().addShutdownHook(restore);

        AudioPlayer audio = null;
        Attributes savedAttributes = null;
        try {
            w.print(Ansi.ALT_SCREEN_ON);
            w.print(Ansi.HIDE_CURSOR);
            w.print(Ansi.CLEAR);
            w.flush();
            savedAttributes = terminal.enterRawMode();

            if (audioStream != null) {
                audio = new AudioPlayer(audioStream);
                audio.start();
                if (audio.isFailed()) {
                    audio.close();
                    audio = null;
                }
            }
            double duration = (double) asset.frameCount() / effectiveFps();
            Timeline timeline = new Timeline(audio, duration);

            runLoop(terminal, w, renderer, timeline, caps);
        } finally {
            if (savedAttributes != null) {
                terminal.setAttributes(savedAttributes);
            }
            if (audio != null) {
                audio.close();
            }
            Runtime.getRuntime().removeShutdownHook(restore);
            w.print(Ansi.SHOW_CURSOR);
            w.print(Ansi.ALT_SCREEN_OFF);
            w.flush();
            terminal.close();
        }
    }

    private double effectiveFps() {
        return asset.fps() > 0 ? asset.fps() : 30.0;
    }

    private void runLoop(Terminal terminal, PrintWriter w, Renderer renderer, Timeline timeline,
                         TerminalCapabilities caps) throws Exception {
        NonBlockingReader reader = terminal.reader();
        int frameCount = asset.frameCount();
        double fps = effectiveFps();

        Layout layout = Layout.compute(terminal, renderer, caps, asset);
        StringBuilder sb = new StringBuilder(layout.screen.width * layout.screen.height * 4);
        int lastRendered = -1;

        while (true) {
            // Pick up terminal resizes.
            if (terminal.getWidth() != layout.cols || terminal.getHeight() != layout.rows) {
                layout = Layout.compute(terminal, renderer, caps, asset);
                w.print(Ansi.CLEAR);
                lastRendered = -1;
            }

            double t = timeline.seconds();
            int idx = (int) Math.floor(t * fps);
            if (idx >= frameCount) {
                if (timeline.isPaused()) {
                    idx = frameCount - 1;
                } else {
                    break;
                }
            }
            if (idx < 0) {
                idx = 0;
            }

            if (idx != lastRendered) {
                renderFrame(renderer, layout, idx, frameCount, sb);
                w.print(Ansi.HOME);
                w.print(sb);
                w.flush();
                lastRendered = idx;
            }

            if (handleInput(reader, timeline)) {
                break;
            }
        }
    }

    private void renderFrame(Renderer renderer, Layout layout, int idx, int frameCount, StringBuilder sb) {
        Frame frame = asset.frameAt(idx);
        Downscaler.downscale(frame, layout.image);
        layout.screen.fill(0f);
        for (int y = 0; y < layout.image.height; y++) {
            for (int x = 0; x < layout.image.width; x++) {
                layout.screen.set(layout.offsetX + x, layout.offsetY + y, layout.image.get(x, y));
            }
        }
        sb.setLength(0);
        renderer.render(layout.screen, colorizer, (double) idx / frameCount, sb);
    }

    /** Reads keys for a short budget (which also paces the loop). Returns true to quit. */
    private boolean handleInput(NonBlockingReader reader, Timeline timeline) throws Exception {
        long end = System.currentTimeMillis() + INPUT_BUDGET_MS;
        long remaining;
        while ((remaining = end - System.currentTimeMillis()) > 0) {
            int c = reader.read(remaining);
            if (c == NonBlockingReader.READ_EXPIRED) {
                break;
            }
            if (c < 0 || c == 'q' || c == 'Q' || c == 3) {
                return true;
            }
            switch (c) {
                case ' ' -> timeline.togglePause();
                case '+', '=' -> timeline.changeSpeed(1.25);
                case '-', '_' -> timeline.changeSpeed(0.8);
                case 27 -> {
                    if (handleEscape(reader, timeline)) {
                        return true;
                    }
                }
                default -> { /* ignore */ }
            }
        }
        return false;
    }

    /** Handles an escape sequence (arrow keys); a lone ESC quits. */
    private boolean handleEscape(NonBlockingReader reader, Timeline timeline) throws Exception {
        int c2 = reader.read(20);
        if (c2 == NonBlockingReader.READ_EXPIRED) {
            return true; // lone ESC: quit
        }
        if (c2 == '[' || c2 == 'O') {
            int c3 = reader.read(20);
            switch (c3) {
                case 'C' -> timeline.seek(SEEK_SECONDS);   // right
                case 'D' -> timeline.seek(-SEEK_SECONDS);  // left
                case 'A' -> timeline.changeSpeed(1.25);    // up
                case 'B' -> timeline.changeSpeed(0.8);     // down
                default -> { /* ignore */ }
            }
        }
        return false;
    }

    /** Holds the current sizing, recomputed on resize. */
    private static final class Layout {
        final int cols;
        final int rows;
        final GrayGrid image;
        final GrayGrid screen;
        final int offsetX;
        final int offsetY;

        private Layout(int cols, int rows, GrayGrid image, GrayGrid screen, int offsetX, int offsetY) {
            this.cols = cols;
            this.rows = rows;
            this.image = image;
            this.screen = screen;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        static Layout compute(Terminal terminal, Renderer renderer, TerminalCapabilities caps, AssetReader asset) {
            int cols = Math.max(1, terminal.getWidth());
            int rows = Math.max(1, terminal.getHeight());
            Renderer.GridSize gs = renderer.gridSize(cols, rows, caps.cellWidthPx, caps.cellHeightPx);
            int gridW = Math.max(1, gs.width());
            int gridH = Math.max(1, gs.height());

            double aspect = (double) asset.width() / asset.height();
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
            return new Layout(cols, rows, new GrayGrid(imgW, imgH), new GrayGrid(gridW, gridH),
                    (gridW - imgW) / 2, (gridH - imgH) / 2);
        }
    }
}
