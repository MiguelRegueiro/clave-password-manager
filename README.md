# Clave

A native Android password manager with a local encrypted vault and built-in Autofill.

## Features

- Protects your vault with Android Keystore encryption and biometric unlock.
- Saves, organizes, searches, and fills accounts across profiles.
- Generates strong passwords and lets you review changes before saving them.
- Matches Autofill requests to the app or website asking for a password.
- Stores vault data on your device. No account, server, or cloud sync is required.

## Requirements

- Android 11 or later with strong biometrics enrolled.
- JDK 17 and Android SDK 36 to build from source.

## Build

```sh
./build-apk.sh --check
./build-apk.sh
```

The APK is created at `artifacts/clave.apk`. Install it on your Android device and enable Clave in the Autofill settings.

## Development

Open `native/` in Android Studio and use JDK 17 as the Gradle JDK.

```sh
./build-apk.sh --test
```

See [the Autofill guide](docs/android-autofill.md) for setup details and the device checklist.

## Security and limitations

Clave is a development project and has not been security audited. It has no recovery, export, or sync. Losing the device, clearing app data, uninstalling the app, or changing enrolled biometrics can permanently lock you out. Use disposable test accounts until you have tested it on your device.

Autofill depends on the browser or app exposing compatible fields. Check the requesting app or website in Clave's picker before approving a fill.
