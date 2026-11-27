package io.github.hyperdream.module;

/** The lens centre lives inside the capsule eroded by lens radius + inset. */
final class GlassNavigationGeometry {
    final float width;
    final float height;
    final float barRadius;
    final float lensRadius;
    private final float movementRadius;

    GlassNavigationGeometry(float width, float height, float inset) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        barRadius = Math.min(this.width, this.height) / 2;
        lensRadius = Math.max(0, Math.min(barRadius * 0.72f, this.width * 0.15f));
        movementRadius = Math.max(0, barRadius - lensRadius - Math.max(0, inset));
    }

    void clamp(float x, float y, float[] destination) {
        float cy = height / 2;
        float dy = Math.max(-movementRadius, Math.min(movementRadius, y - cy));
        float cap = (float) Math.sqrt(Math.max(0, movementRadius * movementRadius - dy * dy));
        destination[0] = Math.max(barRadius - cap, Math.min(width - barRadius + cap, x));
        destination[1] = cy + dy;
    }

    float tabCenter(int index) {
        return width * (Math.max(0, Math.min(2, index)) + 0.5f) / 3;
    }

    int nearestTab(float x) {
        return width <= 0 ? 0 : Math.max(0, Math.min(2, (int) (x * 3 / width)));
    }

    boolean containsLens(float x, float y) {
        float nearestX = Math.max(barRadius, Math.min(width - barRadius, x));
        float dx = x - nearestX;
        float dy = y - height / 2;
        return Math.sqrt(dx * dx + dy * dy) + lensRadius <= barRadius + 0.01f;
    }
}
