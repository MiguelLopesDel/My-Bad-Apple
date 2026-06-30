package dev.badapple.render;

import dev.badapple.render.backends.ITermRenderer;
import dev.badapple.render.backends.KittyRenderer;
import dev.badapple.render.backends.SixelRenderer;
import dev.badapple.render.colorizers.MonoColorizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageBackendsTest {

    private static final char ESC = 27;

    private static GrayGrid whiteGrid(Renderer r) {
        Renderer.GridSize gs = r.gridSize(10, 5, 8, 16);
        GrayGrid grid = new GrayGrid(gs.width(), gs.height());
        grid.fill(1f);
        return grid;
    }

    @Test
    void kittyTransmitsDecodableWhitePng() throws Exception {
        Renderer r = new KittyRenderer();
        GrayGrid grid = whiteGrid(r);
        StringBuilder sb = new StringBuilder();
        r.render(grid, new MonoColorizer(), 0, sb);
        String s = sb.toString();

        assertTrue(s.contains("a=T,f=100"), "kitty transmit+display PNG header");
        BufferedImage img = decodeKitty(s);
        assertEquals(grid.width, img.getWidth());
        assertEquals(grid.height, img.getHeight());
        assertEquals(0xFFFFFF, img.getRGB(0, 0) & 0xFFFFFF, "mono lum=1 is white");
    }

    @Test
    void itermInlineImageDecodes() throws Exception {
        Renderer r = new ITermRenderer();
        GrayGrid grid = whiteGrid(r);
        StringBuilder sb = new StringBuilder();
        r.render(grid, new MonoColorizer(), 0, sb);
        String s = sb.toString();

        assertTrue(s.contains("]1337;File=inline=1"), "iTerm inline image header");
        BufferedImage img = decodeIterm(s);
        assertEquals(grid.width, img.getWidth());
        assertEquals(0xFFFFFF, img.getRGB(grid.width / 2, grid.height / 2) & 0xFFFFFF);
    }

    @Test
    void sixelHasHeaderPaletteAndTerminator() {
        Renderer r = new SixelRenderer();
        GrayGrid grid = whiteGrid(r);
        StringBuilder sb = new StringBuilder();
        r.render(grid, new MonoColorizer(), 0, sb);
        String s = sb.toString();

        assertTrue(s.contains(ESC + "Pq"), "sixel DCS start");
        assertTrue(s.contains("\"1;1;" + grid.width + ";" + grid.height), "raster attributes");
        // White quantizes to cube index (5,5,5) = 215, defined at full intensity.
        assertTrue(s.contains("#215;2;100;100;100"), "white color register");
        assertTrue(s.contains("~"), "full six-row column glyph (63+63)");
        assertTrue(s.endsWith(ESC + "\\"), "sixel ST terminator");
    }

    private static BufferedImage decodeKitty(String s) throws Exception {
        StringBuilder b64 = new StringBuilder();
        String open = ESC + "_G";
        String close = ESC + "\\";
        int i = 0;
        while (true) {
            int g = s.indexOf(open, i);
            if (g < 0) {
                break;
            }
            int semi = s.indexOf(';', g);
            int end = s.indexOf(close, semi);
            b64.append(s, semi + 1, end);
            i = end + close.length();
        }
        byte[] png = Base64.getDecoder().decode(b64.toString());
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static BufferedImage decodeIterm(String s) throws Exception {
        int colon = s.indexOf(':', s.indexOf("]1337;"));
        int bel = s.indexOf((char) 7, colon);
        byte[] png = Base64.getDecoder().decode(s.substring(colon + 1, bel));
        return ImageIO.read(new ByteArrayInputStream(png));
    }
}
