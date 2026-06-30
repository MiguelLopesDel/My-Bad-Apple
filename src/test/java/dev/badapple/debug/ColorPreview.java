package dev.badapple.debug;

import dev.badapple.asset.AssetReader;
import dev.badapple.asset.Frame;
import dev.badapple.render.Colorizer;
import dev.badapple.render.Downscaler;
import dev.badapple.render.GrayGrid;
import dev.badapple.render.colorizers.Colorizers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * Headless visual check for the colorizers: renders one frame in all four color modes into a
 * single 2x2 PNG so the actual colors can be inspected without a terminal.
 *
 * <p>Usage: {@code ColorPreview <outPng> [frameIndex] [assetPath]}
 */
public final class ColorPreview {

    private static final int COLS = 160;
    private static final int SUBROWS = 120; // keeps 4:3
    private static final int SCALE = 4;
    private static final int GAP = 12;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "color-preview.png");
        int frameIndex = args.length > 1 ? Integer.parseInt(args[1]) : 3000;
        Path assetPath = Path.of(args.length > 2 ? args[2] : "src/main/resources/badapple/frames.bin");

        AssetReader asset = AssetReader.load(assetPath);
        int idx = Math.min(frameIndex, asset.frameCount() - 1);
        double t = (double) idx / asset.frameCount();
        Frame frame = asset.frameAt(idx);
        GrayGrid grid = new GrayGrid(COLS, SUBROWS);
        Downscaler.downscale(frame, grid);

        String[][] modes = {
                {"mono", null}, {"hue", null}, {"gradient", "aurora"}, {"lut", "fire"}
        };
        int panelW = COLS * SCALE;
        int panelH = SUBROWS * SCALE;
        int labelH = 22;
        int imgW = panelW * 2 + GAP * 3;
        int imgH = (panelH + labelH) * 2 + GAP * 3;

        BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x101010));
        g.fillRect(0, 0, imgW, imgH);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        for (int m = 0; m < modes.length; m++) {
            int col = m % 2;
            int row = m / 2;
            int x0 = GAP + col * (panelW + GAP);
            int y0 = GAP + row * (panelH + labelH + GAP);
            Colorizer colorizer = Colorizers.create(modes[m][0], modes[m][1]);

            g.setColor(Color.WHITE);
            String label = modes[m][1] == null ? modes[m][0] : modes[m][0] + " / " + modes[m][1];
            g.drawString(label, x0, y0 + 15);

            int py0 = y0 + labelH;
            for (int y = 0; y < SUBROWS; y++) {
                double y01 = (double) y / (SUBROWS - 1);
                for (int x = 0; x < COLS; x++) {
                    double x01 = (double) x / (COLS - 1);
                    int rgb = colorizer.rgb(x01, y01, grid.get(x, y), t) & 0xFFFFFF;
                    g.setColor(new Color(rgb));
                    g.fillRect(x0 + x * SCALE, py0 + y * SCALE, SCALE, SCALE);
                }
            }
        }
        g.dispose();
        ImageIO.write(image, "png", new File(out.toString()));
        System.out.println("wrote " + out + " (frame " + idx + ", t=" + String.format("%.3f", t) + ")");
    }

    private ColorPreview() {
    }
}
