package dev.badapple.render;

import dev.badapple.render.colorizers.Colorizers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorizerTest {

    @Test
    void everyModeIsBlackAtZeroLuminance() {
        for (String mode : new String[]{"mono", "hue", "gradient", "lut"}) {
            Colorizer c = Colorizers.create(mode, null);
            for (double t = 0; t <= 1.0; t += 0.25) {
                assertEquals(0, c.rgb(0.3, 0.7, 0.0, t) & 0xFFFFFF, mode + " must be black at lum=0");
            }
        }
    }

    @Test
    void monoIsGrayscaleProportionalToLuminance() {
        Colorizer mono = Colorizers.create("mono", null);
        assertEquals(0xFFFFFF, mono.rgb(0, 0, 1.0, 0) & 0xFFFFFF);
        int mid = mono.rgb(0, 0, 0.5, 0) & 0xFFFFFF;
        int r = (mid >> 16) & 0xFF, g = (mid >> 8) & 0xFF, b = mid & 0xFF;
        assertEquals(r, g);
        assertEquals(g, b);
        assertEquals(128, r);
    }

    @Test
    void allModesStayWithinRgbRange() {
        for (String mode : new String[]{"mono", "hue", "gradient", "lut"}) {
            Colorizer c = Colorizers.create(mode, "fire");
            for (double y = 0; y <= 1; y += 0.5) {
                for (double x = 0; x <= 1; x += 0.5) {
                    int rgb = c.rgb(x, y, 0.8, 0.6);
                    assertEquals(0, rgb & ~0xFFFFFF, "no bits above 24 for " + mode);
                }
            }
        }
    }

    @Test
    void paletteSamplesEndpoints() {
        Palette p = new Palette(0x000000, 0x808080, 0xFFFFFF);
        assertEquals(0x000000, p.sample(0.0));
        assertEquals(0xFFFFFF, p.sample(1.0));
        int mid = p.sample(0.5);
        assertEquals(0x80, (mid >> 16) & 0xFF);
    }

    @Test
    void hueCycleProducesSaturatedColor() {
        Colorizer hue = Colorizers.create("hue", null);
        int rgb = hue.rgb(0.0, 0.0, 1.0, 0.0) & 0xFFFFFF;
        // pure hue at full value: at least one channel maxed, at least one near zero
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        assertEquals(255, Math.max(r, Math.max(g, b)));
        assertTrue(Math.min(r, Math.min(g, b)) <= 1);
    }
}
