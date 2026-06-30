package dev.badapple.render.colorizers;

import dev.badapple.render.Colorizer;
import dev.badapple.render.Palette;

import java.util.Locale;

/** Builds a {@link Colorizer} from the CLI color mode and optional palette name. */
public final class Colorizers {

    private Colorizers() {
    }

    public static Colorizer create(String mode, String paletteName) {
        String m = mode == null ? "mono" : mode.toLowerCase(Locale.ROOT);
        return switch (m) {
            case "hue" -> new HueCycleColorizer();
            case "gradient" -> new SpatialGradientColorizer(Palette.byName(paletteName, Palette.AURORA));
            case "lut" -> new LuminanceLutColorizer(Palette.byName(paletteName, Palette.FIRE));
            default -> new MonoColorizer();
        };
    }
}
