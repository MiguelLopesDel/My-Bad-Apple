package dev.badapple.render;

import dev.badapple.render.backends.HalfBlockRenderer;
import dev.badapple.render.colorizers.MonoColorizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HalfBlockRendererTest {

    @Test
    void emitsHalfBlocksResetsAndPositioning() {
        GrayGrid grid = new GrayGrid(4, 6); // 4 cols x 3 text rows
        grid.fill(1f);
        StringBuilder out = new StringBuilder();

        new HalfBlockRenderer().render(grid, new MonoColorizer(), 0.0, out);
        String s = out.toString();

        // one half-block glyph per cell
        assertEquals(4 * 3, s.chars().filter(c -> c == '▀').count());
        // three line-position escapes (rows 1..3) and three line resets
        assertTrue(s.contains("[1;1H"), "row 1 positioned");
        assertTrue(s.contains("[2;1H"), "row 2 positioned");
        assertTrue(s.contains("[3;1H"), "row 3 positioned");
        assertEquals(3, countOccurrences(s, "[0m"), "one reset per line");
        // full luminance -> white truecolor foreground
        assertTrue(s.contains("[38;2;255;255;255m"), "white fg for lum=1");
    }

    @Test
    void diffingEmitsColorOnlyOnChange() {
        GrayGrid grid = new GrayGrid(5, 2); // single text row, uniform
        grid.fill(0.5f);
        StringBuilder out = new StringBuilder();

        new HalfBlockRenderer().render(grid, new MonoColorizer(), 0.0, out);
        String s = out.toString();

        // uniform row: foreground color set once despite five cells
        assertEquals(1, countOccurrences(s, "[38;2;128;128;128m"), "fg emitted once");
        assertEquals(1, countOccurrences(s, "[48;2;128;128;128m"), "bg emitted once");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
