package dev.badapple.engine;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the embedded MP3 decodes with JLayer (independent of any audio device, which is
 * absent in headless CI). This covers the decode half of {@link AudioPlayer}.
 */
class AudioDecodeTest {

    @Test
    void embeddedAudioDecodesToPcm() throws Exception {
        try (InputStream in = AudioDecodeTest.class.getResourceAsStream("/badapple/audio.mp3")) {
            assertNotNull(in, "embedded audio.mp3 must be present");
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();

            int frames = 0;
            int sampleRate = -1;
            int channels = -1;
            long samples = 0;
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (sampleRate < 0) {
                    sampleRate = buffer.getSampleFrequency();
                    channels = buffer.getChannelCount();
                }
                samples += buffer.getBufferLength();
                frames++;
                bitstream.closeFrame();
            }

            assertEquals(44100, sampleRate, "sample rate");
            assertEquals(2, channels, "stereo");
            assertTrue(frames > 5000, "expected thousands of MP3 frames, got " + frames);
            // ~219s of stereo audio at 44.1kHz: lots of interleaved samples.
            double seconds = samples / 2.0 / sampleRate;
            assertTrue(seconds > 200 && seconds < 240, "duration ~219s, got " + seconds);
        }
    }
}
