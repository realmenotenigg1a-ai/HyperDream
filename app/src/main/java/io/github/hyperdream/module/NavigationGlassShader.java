package io.github.hyperdream.module;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loaded only on Android 13+. The API 31/32 path never resolves RuntimeShader. */
@TargetApi(33)
final class NavigationGlassShader {
    private final RuntimeShader shader;

    NavigationGlassShader(Context context) throws IOException {
        try (InputStream input = context.getResources().openRawResource(R.raw.navigation_glass)) {
            shader = new RuntimeShader(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        shader.setFloatUniform("density", context.getResources().getDisplayMetrics().density);
    }

    void setBackdrop(Bitmap bitmap, float scaleX, float scaleY) {
        shader.setInputShader("backdrop", new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        shader.setFloatUniform("captureScale", scaleX, scaleY);
    }

    void draw(Canvas canvas, Paint paint, float width, float height, float x, float y,
            float radius, float lightX, float lightY) {
        shader.setFloatUniform("resolution", width, height);
        shader.setFloatUniform("lens", x, y, radius);
        shader.setFloatUniform("light", lightX, lightY);
        paint.setShader(shader);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
    }
}
