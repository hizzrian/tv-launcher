#!/bin/bash
# Build launcher Kaca tanpa Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Hasil: build/kaca.apk
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
VERSION_CODE="${VERSION_CODE:-$(date +%y%m%d%H)}"

rm -rf build && mkdir -p build/classes build/dex

echo "> resources"
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 31 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "1.0" build/res.zip

echo "> java"
javac -nowarn -Xlint:-options -source 11 -target 11 -encoding UTF-8 \
  -classpath "$JAR" -d build/classes $(find src -name '*.java')

echo "> dex"
"$BT/d8" --release --min-api 31 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

echo "> package"
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# Kunci tanda tangan: kaca.keystore + keystore.properties (keduanya lokal, tidak di-commit).
# Bila belum ada, dibuat otomatis dengan kata sandi acak.
if [ ! -f kaca.keystore ]; then
  KACA_KS_PASS="$(openssl rand -hex 16)"
  keytool -genkeypair -keystore kaca.keystore -storepass "$KACA_KS_PASS" -keypass "$KACA_KS_PASS" \
    -alias kaca -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Kaca Launcher" >/dev/null 2>&1
  printf 'KACA_KS_PASS=%s\n' "$KACA_KS_PASS" > keystore.properties
  chmod 600 kaca.keystore keystore.properties
  echo "> keystore baru dibuat: simpan kaca.keystore dan keystore.properties baik-baik"
fi
[ -z "${KACA_KS_PASS:-}" ] && [ -f keystore.properties ] && . ./keystore.properties
: "${KACA_KS_PASS:?Kata sandi keystore tidak ditemukan. Isi keystore.properties atau set KACA_KS_PASS.}"
export KACA_KS_PASS
"$BT/apksigner" sign --ks kaca.keystore --ks-pass env:KACA_KS_PASS --ks-key-alias kaca \
  --out build/kaca.apk build/aligned.apk

ls -lh build/kaca.apk | awk '{print "OK build/kaca.apk", $5}'
