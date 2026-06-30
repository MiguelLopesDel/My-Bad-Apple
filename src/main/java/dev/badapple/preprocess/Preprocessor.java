package dev.badapple.preprocess;

import dev.badapple.asset.AssetFormat;
import dev.badapple.asset.AssetWriter;
import dev.badapple.asset.Frame;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline tool that turns the source mp4 into the embedded runtime asset.
 *
 * <p>Pipeline: ffmpeg decodes the video to a raw 8-bit grayscale stream, each frame is
 * binarized to 1 bit and fed to {@link AssetWriter} (keyframes + XOR deltas, RLE).
 * The audio track is extracted to MP3. Requires {@code ffmpeg}/{@code ffprobe} on PATH;
 * this never runs at player time.
 */
public final class Preprocessor {

    /** Luminance cutoff: pixels at or above this become "light" bits. */
    private static final int THRESHOLD = 128;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: Preprocessor <source-video> <output-dir>");
            System.exit(2);
        }
        Path source = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        if (!Files.exists(source)) {
            System.err.println("source not found: " + source);
            System.exit(2);
        }
        Files.createDirectories(outDir);

        VideoInfo info = probe(source);
        System.out.printf("Source: %dx%d @ %d fps%n", info.width, info.height, info.fps);

        Path binPath = outDir.resolve("frames.bin");
        encodeVideo(source, info, binPath);

        Path audioPath = outDir.resolve("audio.mp3");
        extractAudio(source, audioPath);

        System.out.printf("Asset:  %s (%.2f MB)%n", binPath, Files.size(binPath) / 1_048_576.0);
        System.out.printf("Audio:  %s (%.2f MB)%n", audioPath, Files.size(audioPath) / 1_048_576.0);
    }

    private static void encodeVideo(Path source, VideoInfo info, Path binPath) throws IOException, InterruptedException {
        int frameBytes = info.width * info.height;
        byte[] buffer = new byte[frameBytes];
        Frame frame = new Frame(info.width, info.height);

        Process ffmpeg = new ProcessBuilder(
                "ffmpeg", "-loglevel", "error",
                "-i", source.toString(),
                "-f", "rawvideo", "-pix_fmt", "gray", "-")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        AssetWriter writer = new AssetWriter(binPath, info.width, info.height, info.fps,
                AssetFormat.DEFAULT_KEY_INTERVAL);
        try (DataInputStream in = new DataInputStream(ffmpeg.getInputStream())) {
            while (true) {
                int read = in.readNBytes(buffer, 0, frameBytes);
                if (read == 0) {
                    break;
                }
                if (read < frameBytes) {
                    break; // truncated trailing frame; ignore
                }
                for (int i = 0; i < frameBytes; i++) {
                    frame.set(i, (buffer[i] & 0xFF) >= THRESHOLD);
                }
                writer.add(frame);
                if (writer.frameCount() % 500 == 0) {
                    System.out.printf("  encoded %d frames%n", writer.frameCount());
                }
            }
        }
        int code = ffmpeg.waitFor();
        if (code != 0) {
            throw new IOException("ffmpeg (video) exited with code " + code);
        }
        writer.finish();
        System.out.printf("Encoded %d frames%n", writer.frameCount());
    }

    private static void extractAudio(Path source, Path audioPath) throws IOException, InterruptedException {
        for (String codec : new String[]{"libmp3lame", "mp3"}) {
            Process p = new ProcessBuilder(
                    "ffmpeg", "-loglevel", "error", "-y",
                    "-i", source.toString(),
                    "-vn", "-c:a", codec, "-b:a", "128k",
                    audioPath.toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            if (p.waitFor() == 0) {
                return;
            }
        }
        throw new IOException("ffmpeg failed to extract audio (no working mp3 encoder)");
    }

    private static VideoInfo probe(Path source) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height,r_frame_rate",
                "-of", "csv=p=0",
                source.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        String line;
        try (InputStream in = p.getInputStream()) {
            line = new String(in.readAllBytes()).trim();
        }
        if (p.waitFor() != 0 || line.isEmpty()) {
            throw new IOException("ffprobe failed for " + source);
        }
        String[] parts = line.split(",");
        int width = Integer.parseInt(parts[0].trim());
        int height = Integer.parseInt(parts[1].trim());
        int fps = parseFps(parts[2].trim());
        return new VideoInfo(width, height, fps);
    }

    private static int parseFps(String rational) {
        int slash = rational.indexOf('/');
        if (slash < 0) {
            return Math.round(Float.parseFloat(rational));
        }
        int num = Integer.parseInt(rational.substring(0, slash));
        int den = Integer.parseInt(rational.substring(slash + 1));
        return den == 0 ? 30 : Math.round((float) num / den);
    }

    private record VideoInfo(int width, int height, int fps) {
    }

    private Preprocessor() {
    }
}
