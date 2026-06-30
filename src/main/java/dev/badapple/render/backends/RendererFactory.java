package dev.badapple.render.backends;

import dev.badapple.cli.Args;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Renderer;
import dev.badapple.terminal.TerminalCapabilities;

import java.util.Locale;

/**
 * Chooses a render backend from detected capabilities, honoring an explicit {@code --renderer}
 * override. Auto priority: image protocols (kitty &gt; iTerm &gt; sixel) when available,
 * otherwise half-blocks for color+Unicode terminals, falling back to ASCII.
 *
 * <p>The image backends are added in a later phase; until then those choices fall back to
 * half-blocks so the surface is stable.
 */
public final class RendererFactory {

    private RendererFactory() {
    }

    public static Renderer create(TerminalCapabilities caps, Args args) {
        String choice = args.renderer == null ? "auto" : args.renderer.toLowerCase(Locale.ROOT);
        return switch (choice) {
            case "ascii" -> new AsciiRenderer(caps.colorDepth);
            case "halfblock" -> new HalfBlockRenderer(colorOrDefault(caps.colorDepth));
            // Image backends not yet available: fall back without erroring.
            case "kitty", "sixel", "iterm" -> new HalfBlockRenderer(colorOrDefault(caps.colorDepth));
            default -> auto(caps);
        };
    }

    private static Renderer auto(TerminalCapabilities caps) {
        // Image protocols will take priority here once their backends land.
        if (!caps.unicode || caps.colorDepth == ColorDepth.NONE) {
            return new AsciiRenderer(caps.colorDepth);
        }
        return new HalfBlockRenderer(caps.colorDepth);
    }

    private static ColorDepth colorOrDefault(ColorDepth depth) {
        return depth == ColorDepth.NONE ? ColorDepth.ANSI16 : depth;
    }
}
