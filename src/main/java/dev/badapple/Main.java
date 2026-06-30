package dev.badapple;

import dev.badapple.asset.AssetReader;
import dev.badapple.engine.Player;
import dev.badapple.render.Colorizer;
import dev.badapple.render.Renderer;
import dev.badapple.render.backends.HalfBlockRenderer;
import dev.badapple.render.colorizers.MonoColorizer;

import java.io.InputStream;

/**
 * Entry point for the My Bad Apple terminal player.
 *
 * <p>Loads the embedded asset, picks a renderer and colorizer, and runs the player.
 * Capability detection, color modes and controls are wired in across later phases.
 */
public final class Main {

    private static final String ASSET = "/badapple/frames.bin";
    private static final String AUDIO = "/badapple/audio.mp3";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        try (InputStream in = Main.class.getResourceAsStream(ASSET)) {
            if (in == null) {
                System.err.println("Embedded asset not found: " + ASSET
                        + " (run './gradlew generateAsset')");
                System.exit(1);
                return;
            }
            AssetReader asset = AssetReader.load(in);
            Renderer renderer = new HalfBlockRenderer();
            Colorizer colorizer = new MonoColorizer();
            // Audio stream is owned by the player thread for the whole run; not closed here.
            InputStream audio = Main.class.getResourceAsStream(AUDIO);
            new Player(asset, renderer, colorizer, audio).play();
        }
    }
}
