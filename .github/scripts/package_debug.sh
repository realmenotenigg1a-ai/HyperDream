#!/usr/bin/env bash
set -euo pipefail
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
TOOLS="$SDK_ROOT/build-tools/35.0.0"
SOURCE="app/build/outputs/apk/debug/app-debug.apk"
DEST="build/distribution"
mkdir -p "$DEST"
"$TOOLS/aapt" dump badging "$SOURCE" > "$DEST/PACKAGE.txt"
VERSION=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" "$DEST/PACKAGE.txt" | head -n 1)
[[ "$VERSION" =~ ^[a-zA-Z0-9._-]+$ ]] || { echo 'Invalid APK version'; exit 1; }
REVISION="${GITHUB_SHA:-local}"
REVISION="${REVISION:0:8}"
NAME="HyperDream-${VERSION}-${REVISION}-debug.apk"
cp "$SOURCE" "$DEST/$NAME"
"$TOOLS/apksigner" verify --verbose --print-certs "$DEST/$NAME" > "$DEST/SIGNATURE.txt"
(cd "$DEST" && sha256sum "$NAME" > SHA256SUMS.txt)
printf 'Version: %s\nBuild: %s\nCommit: %s\nSigning: Android debug key (testing only)\nHyperOS 4 on-device verification: not performed\n' \
  "$VERSION" "$REVISION" "${GITHUB_SHA:-local}" > "$DEST/BUILD-INFO.txt"

# Independently installable native observer. The APK alone cannot enter the Rust runtime.
export SOURCE DEST VERSION REVISION
python3 - <<'PY'
import os, zipfile
from pathlib import Path
source, dest = os.environ['SOURCE'], Path(os.environ['DEST'])
name = f"HyperDream-NativeProbe-{os.environ['VERSION']}-{os.environ['REVISION']}.zip"
with zipfile.ZipFile(source) as apk:
    payload = apk.read('lib/arm64-v8a/libhyperdream_runtime.so')
assert payload[:4] == b'\x7fELF', 'Missing native ELF payload'
assert int.from_bytes(payload[18:20], 'little') == 183, 'Payload is not AArch64'
with zipfile.ZipFile(dest / name, 'w', zipfile.ZIP_DEFLATED) as module:
    for path in sorted(Path('native-runtime').iterdir()):
        if path.is_file():
            content = path.read_bytes()
            if path.name == 'module.prop':
                content += f"\n# Build: {os.environ['REVISION']}\n".encode()
            module.writestr(path.name, content)
    module.writestr('lib/arm64-v8a/libhyperdream_runtime.so', payload)
print(f'Packaged native observer: {name}')
PY
NDK="$SDK_ROOT/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin"
unzip -p "$SOURCE" lib/arm64-v8a/libhyperdream_runtime.so > "$DEST/native-verification.so"
"$NDK/llvm-readelf" --dyn-syms "$DEST/native-verification.so" > "$DEST/NATIVE-SYMBOLS.txt"
grep -q ' zn_module$' "$DEST/NATIVE-SYMBOLS.txt"
"$NDK/llvm-readelf" -l "$DEST/native-verification.so" > "$DEST/NATIVE-SEGMENTS.txt"
rm "$DEST/native-verification.so"
(cd "$DEST" && sha256sum HyperDream-NativeProbe-*.zip >> SHA256SUMS.txt)
cp docs/NATIVE-TESTING.md "$DEST/NATIVE-TESTING.md"
