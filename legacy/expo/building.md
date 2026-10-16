# Build an installable app

Expo Go is no longer supported. Expo itself remains the React Native framework and build tooling. A standalone installed app does not need Expo Go, QR scanning or a Metro server.

## Android: easiest cloud APK build

```sh
npm install
npm run apk
```

The command runs EAS Build's `preview` profile, explicitly configured as a non-development APK. First-time setup requires an Expo account, project linkage and an Android signing key; follow the CLI prompts. EAS can manage signing. No Google Play account is needed for direct APK installation. Once successful, the CLI provides a build page/download link. Download the APK on Android and allow installation from that source.

This submits the source to Expo's build service when you run it. No cloud build has been submitted by the assistant.

## Android: local APK, without an Expo account

```sh
npm install
npm run apk:local
```

Requires Linux/macOS, Android SDK, **JDK 17** and network access for uncached Gradle dependencies. The script detects the usual Android Studio SDK locations, or uses `ANDROID_HOME`. It selects JDK 17 from `CLAVE_JAVA_HOME`, `JAVA_HOME`, `.toolchains/jdk-17`, standard system JDK locations, `~/.jdks` or SDKMAN. It pins Gradle to that JDK and sets `NODE_ENV=production`. Java 25/27 are not used: the current Prefab/CMake tooling encountered restricted-native-access failures on Java 25.

If no JDK 17 is installed, download and extract a [Temurin 17 JDK](https://adoptium.net/temurin/releases/?version=17) for your operating system and CPU. Either place its contents at `.toolchains/jdk-17` (so `.toolchains/jdk-17/bin/java` exists), or run `CLAVE_JAVA_HOME=/absolute/path/to/extracted-jdk npm run apk:local`. This does not change your system Java or require an account. `npm run apk:local -- --check` checks toolchain discovery without building.

The script runs Expo prebuild and then `:app:assembleRelease`, which embeds the JavaScript bundle. Keep native customizations in the local module/config plugins, not the generated Android directory.

On success, the script copies the APK to **artifacts/clave-preview.apk**. No attached device is needed to build. This is for private testing: the generated template uses its debug signing key for release unless a real release signing configuration is supplied. Cloud and local builds may use different signing keys; preserve a consistent key for updates. Do not uninstall a vault with important data simply to fix a signing mismatch.

`npm run apk:local -- --help` prints prerequisites without building. Build artifacts are ignored by git. Gradle/ADB are blocked by this assistant sandbox's socket restrictions, so full APK construction remains unverified here.

## Development commands

`npm run android` installs/runs a development build and needs an attached device or emulator. `npm start` serves JavaScript to an already installed development client. Neither command is the standalone APK workflow.

## iOS

An Android APK cannot run on iPhone. iOS requires its own signed build. The usual testing/distribution route is TestFlight, then the App Store; both use Apple signing and the Apple Developer Program. EAS cloud builds can be started from Linux, while local builds require macOS and Xcode.

```sh
npm run build:ios
```

This builds the current iOS app with EAS's production profile after account/signing configuration. It does not automatically submit or release it. The iOS build itself has not been verified here.

The shared screens and iOS vault code exist, but website autofill is not implemented on iOS. It requires a native Credential Provider Extension, AutoFill entitlements, securely shared vault access between the app and extension, and physical-device testing. The Android Kotlin service cannot be reused as that extension.

References: [Expo APK builds](https://docs.expo.dev/build-reference/apk/), [EAS setup and signing](https://docs.expo.dev/build/setup/), [Apple credential provider extensions](https://developer.apple.com/documentation/authenticationservices/ascredentialproviderviewcontroller/).
