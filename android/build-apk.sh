#!/bin/bash
set -eu
variant=${1:-debug}
case "$variant" in debug|release) ;; *) echo "Usage: $0 [debug|release]" >&2; exit 2;; esac
cd "$(dirname "$0")"
sdk=${ANDROID_HOME:-$HOME/Library/Android/sdk}
bt="$sdk/build-tools/34.0.0"
platform="$sdk/platforms/android-34/android.jar"
mkdir -p build/classes build/dex
find build/classes -type f -name '*.class' -delete
cp ../ZCode/config/provider/zcode-builtin.json build/assets/builtin.json
cp ../ZCode/LICENSE ../ZCode/NOTICE.md ../ZCode/THIRD-PARTY-NOTICES.md build/assets/
cat >build/assets/zcode <<'SH'
#!/system/bin/sh
exec "$ZCODE_ANDROID_RUNTIME/bin/node" "$ZCODE_ANDROID_RUNTIME/zcode.cjs" "$@"
SH
cp terminal/{xterm.js,xterm.css,terminal.html} build/assets/
cp terminal/LICENSE build/assets/XTERM-LICENSE
cc=( "$sdk"/ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android26-clang )
"${cc[0]}" -O2 -Wl,-z,max-page-size=16384 terminal/pty.c -o build/assets/pty
"$(/usr/libexec/java_home -v 1.8)/bin/javac" -source 8 -target 8 -classpath "$platform" -d build/classes Studio.java FilesScreen.java LocalRuntime.java TerminalScreen.java
"$bt/d8" --min-api 26 --lib "$platform" --output build/dex build/classes/dev/zcode/android/*.class
manifest=AndroidManifest.xml
if [ "$variant" = release ]; then
  manifest=build/AndroidManifest.xml
  sed 's/android:debuggable="true"/android:debuggable="false"/' AndroidManifest.xml > "$manifest"
fi
"$bt/aapt" package -f -M "$manifest" -I "$platform" -S res -A build/assets -F build/raw.apk
(cd build/dex && zip -q ../raw.apk classes.dex)
"$bt/zipalign" -f 4 build/raw.apk build/aligned.apk
if [ "$variant" = release ]; then
  umask 077
  mkdir -p .signing
  key=.signing/release.p12
  pass=.signing/store-password
  if [ ! -e "$key" ]; then
    [ -s "$pass" ] || openssl rand -hex 32 > "$pass"
    keytool -genkeypair -storetype PKCS12 -keystore "$key" -storepass:file "$pass" -alias zcode-android -keyalg RSA -keysize 3072 -validity 10000 -dname CN=ZCode-Android
  fi
  "$bt/apksigner" sign --ks "$key" --ks-pass "file:$pass" --out build/ZCode-Android-arm64.apk build/aligned.apk
  "$bt/apksigner" verify build/ZCode-Android-arm64.apk
else
if [ ! -f build/debug.jks ]; then keytool -genkeypair -keystore build/debug.jks -storepass android -keypass android -alias debug -keyalg RSA -validity 3650 -dname CN=ZCodeDevelopment; fi
"$bt/apksigner" sign --ks build/debug.jks --ks-pass pass:android --out build/zcode.apk build/aligned.apk
fi
