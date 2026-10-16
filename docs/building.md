# Local Kotlin/Compose build

Run `./build-apk.sh` from the repository. `npm run apk:local` is only a compatibility alias, not an Expo build.

## Prerequisites

- Linux/macOS with Bash, JDK 17 (`java` and `javac`), Android SDK Platform 36 and Build-Tools 36.0.0.
- Internet for the initial Gradle/Maven downloads. The installed app itself has no Internet permission.
- Android 11+ with enrolled strong biometrics for running the app.

On Fedora with Adoptium's repository enabled:

```sh
sudo dnf install temurin-17-jdk
./build-apk.sh --check
./build-apk.sh
```

The script discovers JDK 17 under `/usr/lib/jvm`, `~/.jdks`, SDKMAN, `.toolchains/jdk-17`, or macOS JavaVirtualMachines. Set `CLAVE_JAVA_HOME` for another location. It chooses JDK 17 even if the system default is Java 25/27, without changing global Java settings. Android Studio must also use JDK 17 for Gradle.

The SDK is detected at `~/Android/Sdk` or `~/Library/Android/sdk`, or from `ANDROID_HOME` / `ANDROID_SDK_ROOT`. Install missing SDK components using Android Studio's SDK Manager. The standalone project deliberately does not use React Native, Expo, Prefab, NDK or CMake.

## APK, signing and updates

The build runs `:app:testReleaseUnitTest :app:assembleRelease`, then copies the signed release APK to `artifacts/clave.apk`. No success message/artifact copy occurs on a failed build; an older artifact may still exist from an earlier run, so check the final output.

The first build generates a unique RSA signing key and random password in `.signing/` with private file permissions. Both files are gitignored and must be backed up privately. They are credentials for app updates, not credentials for any online account. Keep the same directory for every future APK. The script refuses to silently replace an incomplete signing setup.

Send **only `artifacts/clave.apk`** using LocalSend. Install it on the phone, then open Clave, create a test vault using biometrics, and enable the Autofill service. Some browsers require selecting external autofill providers in their own settings. Install future APKs over the existing app; don't uninstall to resolve update failures when you need its data.

Package: `com.clave.vault.nativeapp`. The old Expo app used `com.clave.vault`. This intentional separation prevents accidental overwriting or data loss during the migration. There is no old-vault import implemented.

The app doesn't back up or sync passwords. Sending the APK does not transfer the vault, and retaining the signing key is not a way to decrypt a lost vault.

## Tests and Android Studio

`./build-apk.sh --test` runs the JVM tests. Open `native/` in Android Studio for development; Gradle sync may download dependencies. A debug build can use the usual IDE debug signer; release builds use `.signing/` created by the script. Debug and release signing identities differ: use a separate emulator/device for IDE debugging if you already have important data in an installed release.

If Gradle reports a Kotlin compilation failure, retain the **first compiler error and file/line**, not only the final BUILD FAILED line. If installation fails, retain Android's error and do not clear app data as a workaround.

## Verification boundary

JVM tests and direct Android-core compilation have passed. Compose/Gradle release assembly and real-device behavior remain unverified in this environment: its Gradle cache is read-only and network DNS is blocked. A successful local APK build and the checklist in `android-autofill.md` are required before claiming end-to-end functionality.

Build references: [AGP 8.12 compatibility](https://developer.android.com/build/releases/agp-8-12-0-release-notes), [Compose compiler plugin](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [Fedora's Temurin repository package](https://packages.fedoraproject.org/pkgs/adoptium-temurin-java-repository/adoptium-temurin-java-repository/).
