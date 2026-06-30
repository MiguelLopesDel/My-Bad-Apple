package dev.badapple.engine;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.cli.Args;
import dev.badapple.render.Ansi;
import dev.badapple.render.AnsiColor;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Colorizer;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.LineRenderer;
import dev.badapple.render.Renderer;
import dev.badapple.render.backends.RendererFactory;
import dev.badapple.terminal.CapabilityDetector;
import dev.badapple.terminal.TerminalCapabilities;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Drives playback: detects the terminal, picks a renderer, then renders frames against a
 * {@link Timeline}. Keyboard input both paces the loop and handles controls (pause, seek,
 * speed, quit); terminal resizes are picked up live.
 */
public final class Player {

    private static final double SEEK_SECONDS = 5.0;
    private static final long MAX_WAIT_MS = 100;
    private static final long HUD_INTERVAL_NANOS = 150_000_000L;
    private static final int HUD_GREEN = 0x4ADE80;
    private static final int HUD_YELLOW = 0xFACC15;
    private static final int HUD_RED = 0xF87171;

    private final AssetReader asset;
    private final Colorizer colorizer;
    private final InputStream audioStream;
    private final Args args;
    private final FpsMeter fpsMeter = new FpsMeter();
    private boolean showHud;
    private long lastHudNanos;
    private volatile boolean resized;
    private double emaRenderMs;
    private double emaWriteMs;
    private double emaIdleMs;

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

        // Write UTF-8 bytes straight to the tty through one big buffer, flushing once per frame.
        // This avoids per-call char encoding/processing overhead in the hot loop.
        OutputStream raw = terminal.output();
        Charset enc = terminal.encoding();
        OutputStream out = new BufferedOutputStream(raw, 1 << 16);

        Thread restore = new Thread(() -> emitQuietly(raw, enc, Ansi.SHOW_CURSOR + Ansi.ALT_SCREEN_OFF));
        Runtime.getRuntime().addShutdownHook(restore);

        // Pick up resizes via SIGWINCH instead of polling the terminal size every frame.
        terminal.handle(Terminal.Signal.WINCH, sig -> resized = true);

        AudioPlayer audio = null;
        Attributes savedAttributes = null;
        try {
            emit(out, enc, Ansi.ALT_SCREEN_ON + Ansi.HIDE_CURSOR + Ansi.CLEAR);
            out.flush();
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

            runLoop(terminal, out, enc, renderer, timeline, caps);
        } finally {
            if (savedAttributes != null) {
                terminal.setAttributes(savedAttributes);
            }
            if (audio != null) {
                audio.close();
            }
            Runtime.getRuntime().removeShutdownHook(restore);
            emit(out, enc, Ansi.SHOW_CURSOR + Ansi.ALT_SCREEN_OFF);
            out.flush();
            terminal.close();
        }
    }

    private static void emit(OutputStream out, Charset enc, CharSequence s) throws IOException {
        out.write(s.toString().getBytes(enc));
    }

    private static void emitQuietly(OutputStream out, Charset enc, CharSequence s) {
        try {
            out.write(s.toString().getBytes(enc));
            out.flush();
        } catch (IOException ignored) {
            // Best-effort terminal restore on shutdown.
        }
    }

    private double effectiveFps() {
        return asset.fps() > 0 ? asset.fps() : 30.0;
    }

    private void runLoop(Terminal terminal, OutputStream out, Charset enc, Renderer renderer,
                         Timeline timeline, TerminalCapabilities caps) throws Exception {
        int frameCount = asset.frameCount();
        double fps = effectiveFps();

        // Read input on a dedicated thread: plain blocking read() is the one thing JLine does
        // reliably (its read(timeout) overshoots and ready() can under-report on some terminals).
        // The main loop just drains this queue, so input never blocks or skews pacing.
        BlockingQueue<Integer> keys = new LinkedBlockingQueue<>();
        AtomicBoolean reading = new AtomicBoolean(true);
        Thread inputThread = startInputThread(terminal.reader(), keys, reading);

        LineRenderer lineRenderer = renderer instanceof LineRenderer lr ? lr : null;
        Layout layout = Layout.compute(terminal, renderer, caps, asset);
        StringBuilder sb = new StringBuilder(layout.screen.width * layout.screen.height * 4);
        StringBuilder hud = new StringBuilder(64);
        String[] prevLines = newLineBuffer(lineRenderer, layout);
        String[] curLines = newLineBuffer(lineRenderer, layout);
        int lastRendered = -1;
        boolean hudShown = false;
        int lastHudFrame = -1;

        try {
        while (true) {
            // Pick up terminal resizes (flagged by the SIGWINCH handler).
            if (resized) {
                resized = false;
                layout = Layout.compute(terminal, renderer, caps, asset);
                prevLines = newLineBuffer(lineRenderer, layout);
                curLines = newLineBuffer(lineRenderer, layout);
                emit(out, enc, Ansi.CLEAR);
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
                long r0 = System.nanoTime();
                composeScreen(layout, idx);
                double frameT = (double) idx / frameCount;
                sb.setLength(0);
                if (lineRenderer != null) {
                    // Only rewrite rows that actually changed — static bands stay untouched.
                    lineRenderer.renderLines(layout.screen, colorizer, frameT, curLines);
                    for (int r = 0; r < curLines.length; r++) {
                        if (!curLines[r].equals(prevLines[r])) {
                            Ansi.moveTo(sb, r + 1, 1);
                            sb.append(curLines[r]);
                            prevLines[r] = curLines[r];
                        }
                    }
                } else {
                    sb.append(Ansi.HOME);
                    renderer.render(layout.screen, colorizer, frameT, sb);
                }
                long r1 = System.nanoTime();
                if (sb.length() > 0) {
                    emit(out, enc, sb);
                    out.flush();
                }
                long r2 = System.nanoTime();
                emaRenderMs = ema(emaRenderMs, (r1 - r0) / 1e6);
                emaWriteMs = ema(emaWriteMs, (r2 - r1) / 1e6);
                lastRendered = idx;
                fpsMeter.record(r2);
            }

            long now = System.nanoTime();
            if (showHud) {
                if (!hudShown || idx != lastHudFrame || now - lastHudNanos >= HUD_INTERVAL_NANOS) {
                    drawHud(out, enc, hud, layout.cols, fps, timeline, caps.colorDepth, now);
                    lastHudNanos = now;
                    lastHudFrame = idx;
                }
                hudShown = true;
            } else if (hudShown) {
                // Toggled off: wipe the overlay and force a clean repaint underneath.
                emit(out, enc, Ansi.CLEAR);
                out.flush();
                Arrays.fill(prevLines, null);
                lastRendered = -1;
                hudShown = false;
            }

            long p0 = System.nanoTime();
            boolean quit = pace(keys, timeline, pacingBudgetMs(timeline, idx, fps));
            emaIdleMs = ema(emaIdleMs, (System.nanoTime() - p0) / 1e6);
            if (quit) {
                break;
            }
        }
        } finally {
            reading.set(false);
            inputThread.interrupt();
        }
    }

    /** Starts a daemon thread that feeds keystrokes into {@code keys} via blocking reads. */
    private static Thread startInputThread(NonBlockingReader reader, BlockingQueue<Integer> keys,
                                           AtomicBoolean reading) {
        Thread t = new Thread(() -> {
            try {
                while (reading.get()) {
                    int c = reader.read();
                    if (c == NonBlockingReader.EOF) {
                        break;
                    }
                    keys.offer(c);
                }
            } catch (IOException ignored) {
                // Terminal closed; thread exits.
            }
        }, "badapple-input");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String[] newLineBuffer(LineRenderer renderer, Layout layout) {
        return renderer == null ? new String[0] : new String[renderer.lineCount(layout.screen)];
    }

    /** Exponential moving average (seeds on the first sample). */
    private static double ema(double prev, double sample) {
        return prev == 0.0 ? sample : prev * 0.9 + sample * 0.1;
    }

    /** Milliseconds to wait for input before the next frame is due, so frames land on time. */
    private long pacingBudgetMs(Timeline timeline, int idx, double fps) {
        if (timeline.isPaused()) {
            return 40;
        }
        double speed = Math.max(0.05, timeline.speed());
        double aheadMs = ((idx + 1) / fps - timeline.seconds()) / speed * 1000.0;
        long ms = (long) Math.floor(aheadMs);
        if (ms < 1) {
            return 1;
        }
        return Math.min(ms, MAX_WAIT_MS);
    }

    /** Draws the true-FPS overlay in the top-right corner (toggled with Ctrl-H). */
    private void drawHud(OutputStream out, Charset enc, StringBuilder hud, int cols, double targetFps,
                         Timeline timeline, ColorDepth depth, long now) throws IOException {
        double fps = fpsMeter.fps(now);
        String label = String.format(Locale.ROOT, " %.1f fps · %.0f target · %.2fx ",
                fps, targetFps, timeline.speed());
        int col = Math.max(1, cols - label.length() + 1);
        int fg = fps >= 0.9 * targetFps ? HUD_GREEN : fps >= 0.6 * targetFps ? HUD_YELLOW : HUD_RED;

        String timing = String.format(Locale.ROOT, " render %.1f · write %.1f · idle %.0f ms ",
                emaRenderMs, emaWriteMs, emaIdleMs);
        int col2 = Math.max(1, cols - timing.length() + 1);

        hud.setLength(0);
        Ansi.moveTo(hud, 1, col);
        if (depth != ColorDepth.NONE) {
            AnsiColor.appendBg(hud, 0x101010, depth);
            AnsiColor.appendFg(hud, fg, depth);
        }
        hud.append(label).append(Ansi.RESET);
        Ansi.moveTo(hud, 2, col2);
        if (depth != ColorDepth.NONE) {
            AnsiColor.appendBg(hud, 0x101010, depth);
            AnsiColor.appendFg(hud, 0x9CA3AF, depth);
        }
        hud.append(timing).append(Ansi.RESET);
        emit(out, enc, hud);
        out.flush();
    }

    /** Decodes a frame, downscales it, and letterboxes it into the screen grid. */
    private void composeScreen(Layout layout, int idx) {
        Frame frame = asset.frameAt(idx);
        Downscaler.downscale(frame, layout.image);
        layout.screen.fill(0f);
        for (int y = 0; y < layout.image.height; y++) {
            for (int x = 0; x < layout.image.width; x++) {
                layout.screen.set(layout.offsetX + x, layout.offsetY + y, layout.image.get(x, y));
            }
        }
    }

    /**
     * Paces the loop until the budget elapses while staying responsive to input. Waits with
     * {@link LockSupport#parkNanos} — which honors the requested time precisely — instead of a
     * blocking timed read, whose timeout JLine can overshoot badly on some terminals. Returns
     * true to quit.
     */
    private boolean pace(BlockingQueue<Integer> keys, Timeline timeline, long budgetMs) {
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        while (true) {
            Integer c;
            while ((c = keys.poll()) != null) {
                if (handleKey(keys, timeline, c)) {
                    return true;
                }
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            LockSupport.parkNanos(Math.min(remaining, 3_000_000L));
        }
    }

    /** Dispatches one key. Returns true to quit. */
    private boolean handleKey(BlockingQueue<Integer> keys, Timeline timeline, int c) {
        if (c < 0 || c == 'q' || c == 'Q' || c == 3) {
            return true;
        }
        switch (c) {
            case ' ' -> timeline.togglePause();
            case '+', '=' -> timeline.changeSpeed(1.25);
            case '-', '_' -> timeline.changeSpeed(0.8);
            case 8, 127 -> showHud = !showHud; // Ctrl-H (and the Backspace key, which sends DEL)
            case 27 -> {
                return handleEscape(keys, timeline);
            }
            default -> { /* ignore */ }
        }
        return false;
    }

    /** Handles an escape sequence (arrow keys); a lone ESC quits. */
    private boolean handleEscape(BlockingQueue<Integer> keys, Timeline timeline) {
        int c2 = awaitKey(keys, 40);
        if (c2 < 0) {
            return true; // lone ESC: quit
        }
        if (c2 == '[' || c2 == 'O') {
            int c3 = awaitKey(keys, 40);
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

    /** Waits up to {@code budgetMs} for the next queued key; -1 on timeout. */
    private int awaitKey(BlockingQueue<Integer> keys, long budgetMs) {
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        while (true) {
            Integer c = keys.poll();
            if (c != null) {
                return c;
            }
            if (System.nanoTime() >= deadline) {
                return -1;
            }
            LockSupport.parkNanos(1_000_000L);
        }
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

            // A grid unit is rarely square: it's (cellWidth / horizontalSubpixels) by
            // (cellHeight / verticalSubpixels) physical pixels. Correct for that so the image
            // keeps its real aspect instead of being stretched (badly so for 2x2 quadrants).
            int cw = caps.cellWidthPx > 0 ? caps.cellWidthPx : 10;
            int ch = caps.cellHeightPx > 0 ? caps.cellHeightPx : 20;
            double hUnitPx = (double) cols * cw / gridW;
            double vUnitPx = (double) rows * ch / gridH;
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
            return new Layout(cols, rows, new GrayGrid(imgW, imgH), new GrayGrid(gridW, gridH),
                    (gridW - imgW) / 2, (gridH - imgH) / 2);
        }
    }
}
