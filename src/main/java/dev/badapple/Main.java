package dev.badapple;

import dev.badapple.asset.AssetReader;
import dev.badapple.cli.Args;
import dev.badapple.engine.Player;
import dev.badapple.render.Colorizer;
import dev.badapple.render.colorizers.Colorizers;

import java.io.InputStream;

/**
 * Entry point for the My Bad Apple terminal player.
 *
 * <p>Parses args, loads the embedded asset, builds a colorizer and renderer, and runs the
 * player. Capability detection and image backends are wired in across later phases.
 */
public final class Main {

    private static final String ASSET = "/badapple/frames.bin";
    private static final String AUDIO = "/badapple/audio.mp3";

    private Main() {
    }

    public static void main(String[] argv) throws Exception {
        Args args = Args.parse(argv);
        if (args.help) {
            System.out.print(Args.help());
            return;
        }

        try (InputStream in = Main.class.getResourceAsStream(ASSET)) {
            if (in == null) {
                System.err.println("Embedded asset not found: " + ASSET
                        + " (run './gradlew generateAsset')");
                System.exit(1);
                return;
            }
            AssetReader asset = AssetReader.load(in);
            Colorizer colorizer = Colorizers.create(args.color, args.palette);
            // Audio stream is owned by the player thread for the whole run; not closed here.
            InputStream audio = args.noAudio ? null : Main.class.getResourceAsStream(AUDIO);
            new Player(asset, colorizer, audio, args).play();
        }
    }
}
