#!/usr/bin/env bash
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
echo "Android SDK Root: $SDK"

PLATFORM_JAR=$((find "$SDK/platforms" -name "android.jar" 2>/dev/null || true) | sort -V | tail -n 1)
BUILD_TOOLS=$((find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null || true) | sort -V | tail -n 1)

if [ -z "$PLATFORM_JAR" ] || [ -z "$BUILD_TOOLS" ]; then
  SDKMANAGER=$((find "$SDK" -name "sdkmanager" -type f 2>/dev/null || true) | head -n 1)
  if [ -z "$SDKMANAGER" ]; then SDKMANAGER="sdkmanager"; fi
  yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" "platforms;android-35" "build-tools;35.0.0"
  PLATFORM_JAR=$((find "$SDK/platforms" -name "android.jar" 2>/dev/null || true) | sort -V | tail -n 1)
  BUILD_TOOLS=$((find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null || true) | sort -V | tail -n 1)
fi

echo "Using PLATFORM_JAR: $PLATFORM_JAR"
echo "Using BUILD_TOOLS:  $BUILD_TOOLS"

rm -rf build HWPX-Drive-Viewer.apk
mkdir -p build/compiled_res build/gen build/classes build/dex

echo "[1/6] Compiling drawable resources..."
"$BUILD_TOOLS/aapt2" compile app/src/main/res/drawable/ic_launcher.xml -o build/compiled_res/

echo "[2/6] Linking manifest, resources & offline assets..."
"$BUILD_TOOLS/aapt2" link \
  -o build/unaligned.apk \
  -I "$PLATFORM_JAR" \
  --manifest cli/AndroidManifest.xml \
  -A app/src/main/assets \
  --java build/gen \
  -0 arsc \
  build/compiled_res/*.flat

echo "[3/6] Compiling Java sources..."
find build/gen app/src/main/java -name "*.java" > build/sources.txt
javac -source 1.8 -target 1.8 -encoding UTF-8 \
  -classpath "$PLATFORM_JAR" \
  -d build/classes \
  @build/sources.txt

jar cf build/classes.jar -C build/classes .

echo "[4/6] Converting Java bytecode to Dalvik DEX..."
"$BUILD_TOOLS/d8" \
  --min-api 24 \
  --lib "$PLATFORM_JAR" \
  --output build/dex \
  build/classes.jar

echo "[5/6] Packaging DEX & aligning APK..."
cp build/dex/classes.dex ./classes.dex
zip -qj build/unaligned.apk ./classes.dex
rm -f ./classes.dex
"$BUILD_TOOLS/zipalign" -p -f 4 build/unaligned.apk build/aligned.apk

echo "[6/6] Signing installable APK (v1 + v2 + v3)..."
keytool -genkeypair \
  -keystore build/release.keystore \
  -storepass android123 \
  -keypass android123 \
  -alias hwpxkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=HWPX Drive Viewer, OU=Mobile, O=HWPX, L=Seoul, S=Seoul, C=KR"

"$BUILD_TOOLS/apksigner" sign \
  --ks build/release.keystore \
  --ks-key-alias hwpxkey \
  --ks-pass pass:android123 \
  --key-pass pass:android123 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --out HWPX-Drive-Viewer.apk \
  build/aligned.apk

"$BUILD_TOOLS/apksigner" verify HWPX-Drive-Viewer.apk
ls -lh HWPX-Drive-Viewer.apk