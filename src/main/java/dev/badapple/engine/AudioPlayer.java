package dev.badapple.engine;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Decodes the embedded MP3 with JLayer and streams PCM to a {@link SourceDataLine} on a
 * dedicated thread. The line's own frame position is the authoritative playback time, which
 * the player uses as its master clock — video follows audio, not the other way around.
 */
public final class AudioPlayer implements AutoCloseable {

    private final InputStream mp3;

    private volatile SourceDataLine line;
    private volatile int sampleRate;
    private volatile boolean failed;
    private volatile boolean finished;
    private volatile boolean paused;
    private volatile boolean stop;
    private Thread thread;

    public AudioPlayer(InputStream mp3) {
        this.mp3 = new BufferedInputStream(mp3);
    }

    /** Starts decoding and briefly waits for the audio line to open. */
    public void start() {
        thread = new Thread(this::run, "badapple-audio");
        thread.setDaemon(true);
        thread.start();
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (line == null && !failed && System.nanoTime() < deadline) {
            sleepQuietly(2);
        }
    }

    private void run() {
        try {
            Bitstream bitstream = new Bitstream(mp3);
            Decoder decoder = new Decoder();
            Header header;
            while (!stop && (header = bitstream.readFrame()) != null) {
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (line == null) {
                    openLine(buffer.getSampleFrequency(), buffer.getChannelCount());
                }
                handlePause();
                if (stop) {
                    break;
                }
                writeBuffer(buffer);
                bitstream.closeFrame();
            }
            if (line != null && !stop) {
                line.drain();
            }
        } catch (Exception e) {
            failed = true;
        } finally {
            finished = true;
        }
    }

    private void openLine(int sampleFrequency, int channels) throws Exception {
        AudioFormat format = new AudioFormat(sampleFrequency, 16, channels, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine l = (SourceDataLine) AudioSystem.getLine(info);
        l.open(format);
        l.start();
        this.sampleRate = sampleFrequency;
        this.line = l;
    }

    private void writeBuffer(SampleBuffer buffer) {
        short[] pcm = buffer.getBuffer();
        int n = buffer.getBufferLength();
        byte[] bytes = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            short s = pcm[i];
            bytes[i * 2] = (byte) (s & 0xFF);
            bytes[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        line.write(bytes, 0, bytes.length);
    }

    private void handlePause() {
        if (paused && line != null) {
            line.stop();
            while (paused && !stop) {
                sleepQuietly(5);
            }
            if (!stop) {
                line.start();
            }
        }
    }

    /** Playback position in seconds, from the line's frame counter. */
    public double positionSeconds() {
        SourceDataLine l = line;
        if (l == null || sampleRate <= 0) {
            return 0.0;
        }
        return l.getLongFramePosition() / (double) sampleRate;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public void close() {
        stop = true;
        SourceDataLine l = line;
        if (l != null) {
            l.stop();
            l.close();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
