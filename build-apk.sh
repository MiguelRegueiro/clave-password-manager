#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
project_dir="$PWD"
if [[ "${1:-}" == "--help" ]]; then
  printf '%s\n' 'Build Clave locally: ./build-apk.sh' 'Check tools: ./build-apk.sh --check' 'Run tests only: ./build-apk.sh --test' 'Requires JDK 17 and Android SDK 36. No Node, Expo, account, NDK or device needed.'
  exit 0
fi
jdk_dir=""
for candidate in "${CLAVE_JAVA_HOME:-}" "${JAVA_HOME:-}" "$project_dir/.toolchains/jdk-17" /usr/lib/jvm/* "${HOME}/.jdks/"* "${HOME}/.sdkman/candidates/java/"* /Library/Java/JavaVirtualMachines/*/Contents/Home; do
  [[ -n "$candidate" ]] || continue
  [[ -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]] || continue
  java_version="$("$candidate/bin/java" -version 2>&1 || true)"
  if [[ "$java_version" == *'version "17.'* || "$java_version" == *'version "17"'* ]]; then
    jdk_dir="$candidate"
    break
  fi
done
if [[ -z "$jdk_dir" ]]; then
  printf '%s\n' 'JDK 17 not found. Install a JDK 17 distribution or set CLAVE_JAVA_HOME to its directory.' >&2
  exit 1
fi
export JAVA_HOME="$jdk_dir"
export PATH="$JAVA_HOME/bin:$PATH"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_dir" ]]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    if [[ -d "$candidate/platforms" ]]; then sdk_dir="$candidate"; break; fi
  done
fi
if [[ ! -f "$sdk_dir/platforms/android-36/android.jar" || ! -d "$sdk_dir/build-tools/36.0.0" ]]; then
  printf '%s\n' 'Install Android SDK Platform 36 and Build-Tools 36.0.0 in Android Studio → SDK Manager.' 'Set ANDROID_HOME if the SDK is not in its usual location.' >&2
  exit 1
fi
export ANDROID_HOME="$sdk_dir"
export ANDROID_SDK_ROOT="$sdk_dir"
printf 'JDK: %s\nAndroid SDK: %s\n' "$JAVA_HOME" "$ANDROID_HOME"
if [[ "${1:-}" == "--check" ]]; then exit 0; fi
if [[ "${1:-}" == "--test" ]]; then
  bash native/gradlew -p native "-Dorg.gradle.java.home=$JAVA_HOME" --no-daemon :app:testDebugUnitTest
  exit 0
fi
# Keep this unique local key: app updates must have the same signing identity.
umask 077
mkdir -p .signing
if [[ ! -f .signing/clave.jks && ! -f .signing/signing.properties ]]; then
  signing_password="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
  export CLAVE_SIGNING_PASSWORD="$signing_password"
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore .signing/clave.jks -storetype JKS \
    -storepass:env CLAVE_SIGNING_PASSWORD -keypass:env CLAVE_SIGNING_PASSWORD \
    -alias clave -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Clave local, OU=Personal, O=Clave' -noprompt
  printf 'storePassword=%s\n' "$signing_password" > .signing/signing.properties
  unset CLAVE_SIGNING_PASSWORD signing_password
elif [[ ! -f .signing/clave.jks || ! -f .signing/signing.properties ]]; then
  printf '%s\n' 'Incomplete .signing directory. Restore its matching key and properties from backup; do not generate a replacement for an installed app.' >&2
  exit 1
fi
bash native/gradlew -p native "-Dorg.gradle.java.home=$JAVA_HOME" --no-daemon :app:testReleaseUnitTest :app:assembleRelease
apk_path="$project_dir/native/app/build/outputs/apk/release/app-release.apk"
[[ -f "$apk_path" ]] || { printf '%s\n' 'Build did not produce the expected APK.' >&2; exit 1; }
mkdir -p artifacts
cp -- "$apk_path" artifacts/clave.apk
printf '\nAPK ready: %s/artifacts/clave.apk\nSend it with LocalSend and install it on Android 11+.\nBack up .signing securely: it is needed for future app updates.\n' "$project_dir"
