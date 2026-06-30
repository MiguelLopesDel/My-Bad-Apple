package dev.badapple.render;

import dev.badapple.cli.Args;
import dev.badapple.render.backends.AsciiRenderer;
import dev.badapple.render.backends.HalfBlockRenderer;
import dev.badapple.render.backends.QuadrantRenderer;
import dev.badapple.render.backends.RendererFactory;
import dev.badapple.render.backends.SixelRenderer;
import dev.badapple.render.colorizers.MonoColorizer;
import dev.badapple.terminal.TerminalCapabilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendSelectionTest {

    private static Args autoArgs() {
        return Args.parse(new String[]{});
    }

    @Test
    void autoPicksHalfBlockForColorUnicodeTerminalWithoutImage() {
        TerminalCapabilities caps = new TerminalCapabilities();
        caps.unicode = true;
        caps.colorDepth = ColorDepth.TRUECOLOR;
        assertInstanceOf(HalfBlockRenderer.class, RendererFactory.create(caps, autoArgs()));
    }

    @Test
    void autoPrefersSixelWhenAvailable() {
        TerminalCapabilities caps = new TerminalCapabilities();
        caps.unicode = true;
        caps.colorDepth = ColorDepth.TRUECOLOR;
        caps.sixel = true;
        assertInstanceOf(SixelRenderer.class, RendererFactory.create(caps, autoArgs()));
    }

    @Test
    void explicitQuadrantStillAvailable() {
        TerminalCapabilities caps = new TerminalCapabilities();
        caps.unicode = true;
        caps.colorDepth = ColorDepth.TRUECOLOR;
        assertInstanceOf(QuadrantRenderer.class,
                RendererFactory.create(caps, Args.parse(new String[]{"--renderer", "quadrant"})));
    }

    @Test
    void autoFallsBackToAsciiWithoutUnicode() {
        TerminalCapabilities caps = new TerminalCapabilities();
        caps.unicode = false;
        caps.colorDepth = ColorDepth.ANSI256;
        assertInstanceOf(AsciiRenderer.class, RendererFactory.create(caps, autoArgs()));
    }

    @Test
    void autoFallsBackToAsciiWithoutColor() {
        TerminalCapabilities caps = new TerminalCapabilities();
        caps.unicode = true;
        caps.colorDepth = ColorDepth.NONE;
        assertInstanceOf(AsciiRenderer.class, RendererFactory.create(caps, autoArgs()));
    }

    @Test
    void explicitRendererOverrides() {
        TerminalCapabilities caps = new TerminalCapabilities();
        caps.unicode = true;
        caps.colorDepth = ColorDepth.TRUECOLOR;
        assertInstanceOf(AsciiRenderer.class,
                RendererFactory.create(caps, Args.parse(new String[]{"--renderer", "ascii"})));
    }

    @Test
    void to256MapsGraysToRampAndColorsToCube() {
        assertEquals(16, AnsiColor.to256(0, 0, 0));       // black
        assertEquals(231, AnsiColor.to256(255, 255, 255)); // white
        int red = AnsiColor.to256(255, 0, 0);
        assertEquals(196, red); // top of the 6x6x6 cube red corner
    }

    @Test
    void to16PicksBrightForLightColors() {
        assertEquals(0, AnsiColor.to16(0, 0, 0));
        int brightWhite = AnsiColor.to16(255, 255, 255);
        assertTrue(brightWhite >= 8, "light colors use the bright range");
    }

    @Test
    void asciiRendererEmitsRampCharacters() {
        GrayGrid grid = new GrayGrid(6, 2);
        grid.fill(1f);
        StringBuilder out = new StringBuilder();
        new AsciiRenderer(ColorDepth.TRUECOLOR).render(grid, new MonoColorizer(), 0.0, out);
        assertTrue(out.toString().contains("@"), "full luminance maps to the densest ramp char");
    }
}
