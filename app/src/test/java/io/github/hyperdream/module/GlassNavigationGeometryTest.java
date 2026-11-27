package io.github.hyperdream.module;

import static org.junit.Assert.*;
import java.util.Random;
import org.junit.Test;

public final class GlassNavigationGeometryTest {
    @Test
    public void draggedLensCannotLeaveCapsuleIncludingRoundedEnds() {
        Random random = new Random(2847);
        float[] point = new float[2];
        for (int width : new int[]{240, 320, 480, 1080}) {
            for (int height : new int[]{72, 90, 128}) {
                GlassNavigationGeometry geometry = new GlassNavigationGeometry(width, height, 2);
                for (int i = 0; i < 3000; i++) {
                    geometry.clamp((random.nextFloat() - 0.5f) * width * 20,
                            (random.nextFloat() - 0.5f) * height * 20, point);
                    assertTrue(geometry.containsLens(point[0], point[1]));
                    assertTrue(point[0] - geometry.lensRadius >= 0);
                    assertTrue(point[0] + geometry.lensRadius <= width);
                    assertTrue(point[1] - geometry.lensRadius >= 0);
                    assertTrue(point[1] + geometry.lensRadius <= height);
                }
            }
        }
    }

    @Test
    public void tabDestinationsAndOutOfRangeInputStayBounded() {
        GlassNavigationGeometry geometry = new GlassNavigationGeometry(300, 90, 2);
        assertEquals(50f, geometry.tabCenter(0), 0f);
        assertEquals(150f, geometry.tabCenter(1), 0f);
        assertEquals(250f, geometry.tabCenter(2), 0f);
        assertEquals(0, geometry.nearestTab(-1000));
        assertEquals(2, geometry.nearestTab(1000));
        assertEquals(1, geometry.nearestTab(150));
        assertEquals(0, new GlassNavigationGeometry(0, 0, 2).nearestTab(0));
    }
}
