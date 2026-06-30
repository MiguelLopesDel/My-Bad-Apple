package dev.badapple.render.backends;

import dev.badapple.cli.Args;
import dev.badapple.render.ColorDepth;
import dev.badapple.render.Renderer;
import dev.badapple.terminal.TerminalCapabilities;

import java.util.Locale;

/**
 * Chooses a render backend from detected capabilities, honoring an explicit {@code --renderer}
 * override. Auto prefers quadrant blocks (2x2 subpixels per cell — the sharpest text option,
 * and fast enough for smooth 30fps), falling back to half-blocks and then ASCII on terminals
 * without Unicode or color. The image protocols (kitty/iTerm/sixel) are higher fidelity but
 * re-encode every frame and can't sustain the frame rate, so they're opt-in via
 * {@code --renderer} rather than auto-selected.
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
        if (!caps.unicode || caps.colorDepth == ColorDepth.NONE) {
            return new AsciiRenderer(caps.colorDepth);
        }
        return new QuadrantRenderer(caps.colorDepth);
    }

    private static ColorDepth colorOrDefault(ColorDepth depth) {
        return depth == ColorDepth.NONE ? ColorDepth.ANSI16 : depth;
    }
}
