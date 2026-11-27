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
}
