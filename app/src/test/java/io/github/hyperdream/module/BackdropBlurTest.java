package io.github.hyperdream.module;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public final class BackdropBlurTest {
    @Test
    public void constantBackdropIsUnchangedAndSourceNotMutated() {
        int[] input = new int[15];
        Arrays.fill(input, 0xFF123456);
        int[] output = new int[15];
        BackdropBlur.apply(input, new int[15], output, 5, 3, 3);
        assertArrayEquals(input, output);
        for (int pixel : input) { assertEquals(0xFF123456, pixel); }
    }

    @Test
    public void realContrastingPixelsAreBlurredAndZeroRadiusIsIdentity() {
        int[] input = {0xFF000000, 0xFFFFFFFF, 0xFF000000};
        int[] output = new int[3];
        BackdropBlur.apply(input, new int[3], output, 3, 1, 1);
        assertArrayEquals(new int[]{0xFF555555, 0xFF555555, 0xFF555555}, output);
        BackdropBlur.apply(input, new int[3], output, 3, 1, 0);
        assertArrayEquals(input, output);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aliasedBuffersAreRejected() {
        int[] same = new int[4];
        BackdropBlur.apply(same, same, new int[4], 2, 2, 1);
    }

    @Test
    public void frostSuppressesFineTextLikeStripesWithoutRepeatingSharpEdges() {
        int width = 120, height = 32;
        int[] input = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) { input[y * width + x] = (x % 2 == 0) ? 0xFFFFFFFF : 0xFF000000; }
        }
        int[] output = new int[input.length];
        BackdropBlur.frost(input, new int[input.length], output, width, height, 10);
        int minimum = 255, maximum = 0;
        for (int x = 40; x < 80; x++) {
            int value = output[16 * width + x] & 255;
            minimum = Math.min(minimum, value); maximum = Math.max(maximum, value);
        }
        assertTrue("High-frequency glyph edges should become frost, not copies", maximum - minimum <= 3);
    }

    @Test
    public void frostedSingleGlyphStrokeHasNoSecondaryGhostPeaks() {
        int width = 121;
        int[] input = new int[width];
        Arrays.fill(input, 0xFF000000);
        input[60] = 0xFFFFFFFF;
        int[] output = new int[width];
        BackdropBlur.frost(input, new int[width], output, width, 1, 6);
        for (int x = 60; x < width - 1; x++) {
            assertTrue((output[x + 1] & 255) <= (output[x] & 255));
        }
    }
}
