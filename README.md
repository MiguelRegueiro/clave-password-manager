# Clave — native Android password manager

Kotlin + Jetpack Compose. Local encrypted vault, strong biometric unlock, profiles, password generation and an Android Autofill service. No Expo, React Native, JavaScript runtime, account, cloud build, backend or network permission in the app.

## Build an APK on Fedora

JDK 17 and Android SDK 36 are required. Your SDK and installed Temurin 17 are detected automatically. If Java is missing and Fedora's Adoptium repository is enabled: `sudo dnf install temurin-17-jdk`.

```sh
./build-apk.sh --check
./build-apk.sh
```

The first build downloads Gradle 8.13 and Maven dependencies. No Android NDK/CMake or Node installation is required. The optional existing alias `npm run apk:local` runs the same script; `npm install` is not needed.

On **successful** completion:

```text
artifacts/clave.apk
```

Send that APK with LocalSend, open it on your Android phone and allow installation from that source when Android asks. Requires **Android 11+ and enrolled strong biometrics** (normally a fingerprint). No Expo Go or computer connection is needed to run the installed app. Enable Clave in Android's autofill settings; some browsers also need their third-party-provider setting enabled.

The script creates a unique local signing key in `.signing/` on its first build and reuses it. **Back up that directory securely and never publish it.** Future updates must use the same key. Send only the APK, not the key or project folder. Signing is automatic and local; there are no EAS credentials.

## What is implemented

- Neutral Material 3 interface: searchable vault, account details/editor, generator, profiles and setup.
- Create, edit and delete accounts; create, rename and delete empty profiles.
- Biometric-protected Android Keystore key wrapping an AES-256-GCM vault in private no-backup storage.
- Fresh biometric authentication to open an account, with password reveal/copy controls.
- Lock on background and after two minutes of inactivity; account details expire after 30 seconds.
- Sensitive clipboard marking and best-effort 30-second cleanup. Screenshots are allowed, including visible secrets; share them carefully.
- Native Autofill picker, generation, profile selection and save/update confirmation for recognized forms.
- Generated replacements retain the previous password until confirmed, with a rollback option.
- Exact HTTPS-origin matching, native package/signature matching, and revision checks against stale writes.

Autofill depends on the browser/app exposing compatible fields; it is not guaranteed on every site. Clave does not reset passwords or submit forms for you. Web-domain association is not automatically verified: the picker explicitly asks you to check the requesting app and domain. See [the architecture and device checklist](docs/android-autofill.md).

## Important limits

This is a development implementation, not a security-audited production password manager. **Use disposable test accounts until the device checklist passes.** There is no recovery, encrypted export or sync yet. Uninstalling, clearing app data, losing the phone or changing enrolled biometrics can permanently lose access. The APK is the application, **not a backup of the vault**.

The native app uses `com.clave.vault.nativeapp`, separate from the old Expo application's package. It installs alongside it and does not read, overwrite or import its vault. Preserve the old installation if you need its data. Old sources/generated Android project are archived in `legacy/expo/`; the old `node_modules/` directory is unused and was left untouched.

## Development

Open **`native/`** in Android Studio and select **JDK 17** as the Gradle JDK. There is no prebuild/code-generation step that replaces your Kotlin source.

```sh
./build-apk.sh --test
```

This runs JVM tests through Gradle. The normal APK command runs release unit tests before packaging. For a full lint check, use `./gradlew :app:lintRelease` from `native/` with JDK 17 and the Android SDK configured.

Source layout:

```text
native/app/src/main/java/com/clave/
  vault/       Compose UI, lifecycle/session controller, immutable document operations
  autofill/    Autofill service/picker, form parser, Keystore vault, crypto
native/app/src/test/   Crypto, matching, generator and document tests
build-apk.sh          Local toolchain discovery, signing, tests and APK packaging
```

## Verification status

Migration checks: toolchain discovery and shell syntax pass; 12 JVM tests pass via direct Kotlin compilation; the vault/autofill sources and document model compile directly against Android API 36; resources compile/link with AAPT2 using a temporary package-qualified manifest. This does **not** verify the Compose app, Gradle linking, manifest merge, R8, installation or biometric/autofill behavior on a phone.

Full APK assembly could not run in the assistant environment because the Gradle home is read-only and dependency-download DNS is unavailable. No new APK has been produced here. Device acceptance remains pending. See [build troubleshooting](docs/building.md).
