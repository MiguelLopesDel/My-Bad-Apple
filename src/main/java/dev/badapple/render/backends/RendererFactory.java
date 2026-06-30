package dev.badapple.render.backends;

import dev.badapple.cli.Args;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Renderer;
import dev.badapple.terminal.TerminalCapabilities;

import java.util.Locale;

/**
 * Chooses a render backend from detected capabilities, honoring an explicit {@code --renderer}
 * override. Auto prefers a real image protocol when the terminal has one (kitty &gt; iTerm &gt;
 * sixel) — full pixel resolution and smooth shading, and fast enough at the default mono color
 * now that the loop is properly paced. Without an image protocol it falls back to half-blocks
 * (smooth grayscale) and then ASCII. Quadrant blocks are sharper-but-blockier and stay opt-in
 * via {@code --renderer quadrant}.
 */
public final class RendererFactory {

    private RendererFactory() {
    }

    public static Renderer create(TerminalCapabilities caps, Args args) {
        String choice = args.renderer == null ? "auto" : args.renderer.toLowerCase(Locale.ROOT);
        return switch (choice) {
            case "ascii" -> new AsciiRenderer(caps.colorDepth);
            case "halfblock" -> new HalfBlockRenderer(colorOrDefault(caps.colorDepth));
            case "quadrant" -> new QuadrantRenderer(colorOrDefault(caps.colorDepth));
            case "kitty" -> new KittyRenderer();
            case "sixel" -> new SixelRenderer();
            case "iterm" -> new ITermRenderer();
            default -> auto(caps);
        };
    }

    private static Renderer auto(TerminalCapabilities caps) {
        if (caps.kitty) {
            return new KittyRenderer();
        }
        if (caps.iterm) {
            return new ITermRenderer();
        }
        if (caps.sixel) {
            return new SixelRenderer();
        }
        if (!caps.unicode || caps.colorDepth == ColorDepth.NONE) {
            return new AsciiRenderer(caps.colorDepth);
        }
        return new HalfBlockRenderer(caps.colorDepth);
    }

    private static ColorDepth colorOrDefault(ColorDepth depth) {
        return depth == ColorDepth.NONE ? ColorDepth.ANSI16 : depth;
    }
}
