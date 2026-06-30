package dev.badapple.engine;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.render.Colorizer;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.Renderer;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.InputStream;
import java.io.PrintWriter;

/**
 * Drives playback: sizes the image to the terminal, then renders frames against a master
 * {@link PlaybackClock}. Audio (when present) is the clock, so video follows the real sound
 * output; frames are dropped whenever the clock has moved past them.
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
    private final InputStream audioStream;

    public Player(AssetReader asset, Renderer renderer, Colorizer colorizer, InputStream audioStream) {
        this.asset = asset;
        this.renderer = renderer;
        this.colorizer = colorizer;
        this.audioStream = audioStream;
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

        AudioPlayer audio = null;
        try {
            w.print(ALT_SCREEN_ON);
            w.print(HIDE_CURSOR);
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

            runLoop(terminal, w, clock, audio);
        } finally {
            if (audio != null) {
                audio.close();
            }
            Runtime.getRuntime().removeShutdownHook(restore);
            w.print(SHOW_CURSOR);
            w.print(ALT_SCREEN_OFF);
            w.flush();
            terminal.close();
        }
    }

    private void runLoop(Terminal terminal, PrintWriter w, PlaybackClock clock, AudioPlayer audio)
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
                w.print(CURSOR_HOME);
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
