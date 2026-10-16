# Clave

React Native + Expo SDK 57 password manager for **Android 11+**, with a neutral charcoal interface, profiles, generation and a native Android autofill implementation. No backend or cloud sync.

## Run the real Android app

Website autofill requires installing Clave itself. Expo Go cannot register its native service. The installed app and autofill picker share one persistent encrypted vault.

```sh
npm install
npx expo prebuild --platform android --no-install
npm run android
```

Use Node 22.22.2+, the JDK required by the installed Android Gradle plugin, and the Android SDK. After installing the development build, `npm start` starts Metro; its QR opens **Clave's development client**, not Expo Go.

For a standalone APK without Metro, run `npm run apk` (Expo cloud build; first run requires Expo login/project configuration and signing setup). Or run `npm run apk:local` to build on your Linux/macOS computer with the Android SDK/JDK, without an Expo account. The local command generates `artifacts/clave-preview.apk` on success. Its default generated signing key is for testing, not production distribution. No APK has been produced or cloud build started here. See [build instructions](docs/building.md).

1. Open Clave, enroll strong biometrics if needed, and create the vault.
2. Open **Perfiles → Autocompletar en Android → Activar Clave** and select it in Android settings.
3. If your browser offers a setting to use other autofill services, enable it and restart the browser.
4. On a supported HTTPS login/reset form, tap the password field and select **Clave · Elegir cuenta o generar contraseña**.
5. Authenticate, confirm the requesting app/domain, and select an account or generate a password. Choose a profile when saving.

## Implemented

- Android AutofillService with authenticated account picker, generation and Android save requests.
- Username/profile confirmation and saving generated credentials **before** returning them to the form.
- Exact-origin matching; existing accounts grouped by profile.
- A shared AES-256-GCM vault in app-private no-backup storage. A Keystore RSA-OAEP wrapping key requires a fresh biometric CryptoObject operation to unwrap the vault key.
- Atomic writes and revision checks to reject stale app/browser edits.
- Generated updates preserve the previous password until the user confirms the website change. Pending account details offer confirmation and rollback.
- In-app add/edit/delete, profile creation/filtering, search, reveal/copy, background/inactivity locking and timed credential display.

Saving in Clave does not change a website's password. The website still handles reset codes and must accept the new password. Generated entries remain pending until confirmed; autofill cannot reliably infer server acceptance.

## Verification

Passed in this workspace: TypeScript, eight JavaScript tests, SDK 57 Android Metro export, Expo prebuild/module discovery, direct Kotlin compilation of the native vault/crypto/parser/service/picker against the Android SDK, and seven JVM tests for matching, generation, AES-GCM integrity and RSA-OAEP wrapping.

**Not verified:** full APK assembly (including Expo Kotlin bridge and manifest merge), real-device Keystore/biometrics, browser autofill/save behavior and device UI. Gradle failed before compilation because the sandbox cannot start its network-based file-lock service. ADB also cannot bind its local socket. These remain acceptance gates.

```sh
npm run typecheck
npm test
npx expo export --platform android
# In a normal local Android environment, after prebuild:
cd android
./gradlew :clave-autofill:testDebugUnitTest :app:assembleDebug
```

See [Android architecture and acceptance plan](docs/android-autofill.md).

## Compatibility and limitations

The parser is conservative: exact HTTPS origins on the standard port, one origin per form, at most one username and three password fields. It refuses missing/insecure web schemes, mixed-origin fields and ambiguous multi-password forms. Native app matching also pins package and signing certificate. Not every website, iframe, browser or multi-step login exposes usable fields; copy/paste remains the fallback. There is no passkey provider, desktop extension or iOS autofill extension.

Domain-to-app association is **not automatically verified** via Digital Asset Links or browser certificate allowlists. Web requests always display the requesting package and claimed domain and require confirmation, following Android's documented fallback. Only confirm a known browser and the exact site you opened. Automatic verification remains a production-hardening item.

There is no recovery key/master-password fallback, export or sync. Enrollment changes, uninstalling or losing the device may make data unrecoverable. Use test credentials until physical-device acceptance and security review are complete. Plaintext exists in process memory during authenticated sessions; garbage-collected strings cannot be securely erased. Clipboard clearing is best effort when the OS suspends execution.

An earlier native Android vault in the old AsyncStorage/SecureStore format is never silently overwritten: creation is blocked if that old storage exists. Migration is not implemented. Previous Expo Go demo data was memory-only. The existing iOS storage code remains but is not this implementation's focus.

## Expo Go and iOS

The Expo Go demo and its memory-only storage have been removed. Opening the project in Expo Go shows installation instructions only. Expo remains the framework/build tooling; the phone runs the installed Clave application.

iOS can reuse the React Native screens and its existing vault code, but the iOS Credential Provider Extension and shared storage integration are not implemented. `npm run build:ios` configures a cloud store build of the current app; it does not implement autofill or submit to TestFlight. Apple signing and Developer Program membership are required for that distribution route. Local iOS builds use macOS/Xcode. See [build instructions](docs/building.md).

## Source

- `App.tsx`, `src/AutofillSettings.tsx`: neutral UI and service setup.
- `src/autofill.ts`, `src/vault.ts`: bridge and platform routing.
- `modules/clave-autofill/android/`: service, biometric picker, shared vault and JVM tests.
- `plugins/withClaveAndroid.js`: reproducible Android minimum-version configuration.
- `fixtures/autofill.html`: disposable HTTPS form fixture; never sends passwords to a server.

References: [Android autofill services](https://developer.android.com/identity/autofill/autofill-services), [web security and authentication](https://developer.android.com/reference/android/service/autofill/AutofillService), [biometric cryptographic operations](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject).
