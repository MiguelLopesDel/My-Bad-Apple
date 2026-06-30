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
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.InputStream;
import java.io.PrintWriter;

/**
 * Drives playback: detects the terminal, picks a renderer, sizes the image, then renders
 * frames against a master {@link PlaybackClock}. Audio (when present) is the clock, so video
 * follows the real sound output; frames are dropped whenever the clock has moved past them.
 */
public final class Player {

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
        try {
            w.print(Ansi.ALT_SCREEN_ON);
            w.print(Ansi.HIDE_CURSOR);
            w.print(Ansi.CLEAR);
            w.flush();

            PlaybackClock clock;
            if (audioStream != null) {
                audio = new AudioPlayer(audioStream);
                audio.start();
            }
            if (audio != null && !audio.isFailed()) {
                final AudioPlayer a = audio;
                clock = a::positionSeconds;
            } else {
                clock = new PlaybackClock.Wall();
            }

            runLoop(terminal, w, renderer, clock);
        } finally {
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

    private void runLoop(Terminal terminal, PrintWriter w, Renderer renderer, PlaybackClock clock)
            throws InterruptedException {
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
        int lastRendered = -1;

        while (true) {
            double t = clock.seconds();
            int idx = (int) Math.floor(t * fps);
            if (idx >= frameCount) {
                break;
            }
            if (idx < 0) {
                idx = 0;
            }
            if (idx > lastRendered) {
                Frame frame = asset.frameAt(idx);
                Downscaler.downscale(frame, image);
                screen.fill(0f);
                for (int y = 0; y < imgH; y++) {
                    for (int x = 0; x < imgW; x++) {
                        screen.set(offsetX + x, offsetY + y, image.get(x, y));
                    }
                }
                sb.setLength(0);
                renderer.render(screen, colorizer, (double) idx / frameCount, sb);
                w.print(Ansi.HOME);
                w.print(sb);
                w.flush();
                lastRendered = idx;
            }

            // Sleep until the next frame boundary, re-reading the clock to stay in sync.
            double next = (lastRendered + 1) / fps;
            long ms = (long) ((next - clock.seconds()) * 1000);
            Thread.sleep(ms > 1 ? Math.min(ms, 50) : 1);
        }
    }
}
