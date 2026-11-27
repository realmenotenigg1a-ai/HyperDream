package io.github.hyperdream.module;

/** Allocation-free backdrop prefilter shared by AGSL and bitmap renderers. */
final class BackdropBlur {
    private BackdropBlur() {}

    /** Three box passes approximate a Gaussian; working is disposable capture-pixel storage. */
    static void frost(int[] working, int[] scratch, int[] output, int width, int height, int radius) {
        apply(working, scratch, output, width, height, radius);
        apply(output, scratch, working, width, height, radius);
        apply(working, scratch, output, width, height, radius);
    }

    static void apply(int[] source, int[] scratch, int[] output, int width, int height, int radius) {
        long length = (long) width * height;
        if (width <= 0 || height <= 0 || length > Integer.MAX_VALUE || radius < 0 || radius > 32
                || source.length < length || scratch.length < length || output.length < length
                || source == scratch || source == output || scratch == output) {
            throw new IllegalArgumentException("Invalid blur buffers");
        }
        int window = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            int a = 0, r = 0, g = 0, b = 0;
            for (int k = -radius; k <= radius; k++) {
                int color = source[y * width + clamp(k, width)];
                a += color >>> 24; r += (color >> 16) & 255;
                g += (color >> 8) & 255; b += color & 255;
            }
            for (int x = 0; x < width; x++) {
                scratch[y * width + x] = pack(a / window, r / window, g / window, b / window);
                int old = source[y * width + clamp(x - radius, width)];
                int next = source[y * width + clamp(x + radius + 1, width)];
                a += (next >>> 24) - (old >>> 24);
                r += ((next >> 16) & 255) - ((old >> 16) & 255);
                g += ((next >> 8) & 255) - ((old >> 8) & 255);
                b += (next & 255) - (old & 255);
            }
        }
        for (int x = 0; x < width; x++) {
            int a = 0, r = 0, g = 0, b = 0;
            for (int k = -radius; k <= radius; k++) {
                int color = scratch[clamp(k, height) * width + x];
                a += color >>> 24; r += (color >> 16) & 255;
                g += (color >> 8) & 255; b += color & 255;
            }
            for (int y = 0; y < height; y++) {
                output[y * width + x] = pack(a / window, r / window, g / window, b / window);
                int old = scratch[clamp(y - radius, height) * width + x];
                int next = scratch[clamp(y + radius + 1, height) * width + x];
                a += (next >>> 24) - (old >>> 24);
                r += ((next >> 16) & 255) - ((old >> 16) & 255);
                g += ((next >> 8) & 255) - ((old >> 8) & 255);
                b += (next & 255) - (old & 255);
            }
        }
    }

    private static int clamp(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }

    private static int pack(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
