"""Compile the AGSL subset with Skia and verify it samples changing app pixels.

This does not replace Android GPU/device visual tests. Run with skia-python==144.1
and numpy installed; Linux also needs the platform EGL/OpenGL runtime libraries.
"""
from pathlib import Path
import numpy as np
import skia

source = Path("app/src/main/res/raw/navigation_glass.agsl").read_text()
effect = skia.RuntimeEffect.MakeForShader(source)
builder = skia.RuntimeShaderBuilder(effect)
for name, value in {
    "resolution": [360.0, 90.0],
    "captureScale": [1.0, 1.0],
    "lens": [60.0, 45.0, 32.4],
    "light": [24.0, -20.0],
    "density": 1.0,
}.items():
    builder.setUniform(name, value)


def render(color):
    backdrop = skia.Surface(360, 90)
    backdrop.getCanvas().clear(color)
    builder.setChild("backdrop", backdrop.makeImageSnapshot().makeShader())
    surface = skia.Surface(360, 90)
    surface.getCanvas().clear(skia.ColorTRANSPARENT)
    surface.getCanvas().drawRect(skia.Rect.MakeWH(360, 90), skia.Paint(Shader=builder.makeShader()))
    return surface.makeImageSnapshot().toarray()


red = render(skia.ColorRED)
blue = render(skia.ColorBLUE)
assert np.any(red != blue), "Glass must respond to backdrop colors"
assert red[0, 0, 3] == 0, "Capsule corners must remain transparent"
assert red[45, 180, 3] == 255, "Capsule interior must be visible"
print("PASS: glass shader compiles, samples changing pixels and clips to capsule")
