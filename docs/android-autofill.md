# Android autofill architecture and acceptance

## Flow

```text
Browser / Android app → Android Autofill → ClaveAutofillService
  → authenticated suggestion + SaveInfo
  → AutofillActivity → biometrics → target confirmation → profile/account picker
  → shared NativeVault → FillResponse → Android fills the original form

Form submission → Android save prompt → AutofillActivity → profile selection → NativeVault
Compose screens → MainActivity → same NativeVault
```

The service never decrypts in the background. One-use request tokens expire after two minutes; intents contain only those tokens. No secrets are logged. The picker is not exported. The service is exported with the system-only BIND_AUTOFILL_SERVICE permission.

The native picker uses a biometric CryptoObject tied to the Keystore private wrapping key. Its unwrapped AES key is cleared on close, background or a one-minute timeout. The main activity holds a native session while unlocked, closes it on background/two-minute inactivity and requires fresh biometrics for account details. There is no JavaScript bridge. Decrypted records exist in JVM memory while unlocked; Compose does not put passwords in saved-instance-state bundles.

Writes use a fresh AES-GCM nonce and AtomicFile in noBackupFilesDir. Revisions prevent stale app edits overwriting browser saves. The app also disables Android backups. Garbage-collected plaintext strings cannot be forcibly erased.

## Matching and form recognition

Only explicit HTTPS origins without user info or nonstandard ports are accepted. Matching is exact and case/IDN normalized. Credentials for example.com do not match login.example.com or example.com.evil.test. Mixed-origin/native-web fields, multiple usernames and ambiguous current/new-password fields are refused. Native app credentials require the same package and signing certificate.

Web-domain association is not independently verified. The picker requires explicit confirmation of the requesting package and claimed domain, as described in Android's fallback guidance. This is weaker than automatic Digital Asset Links/trusted-browser verification and remains a hardening task. Never treat the confirmation as proof of a verified domain.

Generating requires a username and profile. Credentials are saved before filling. Updating an existing entry requires confirmation and retains the previous password until the website change is confirmed; account details offer rollback. Android save requests also require review and update confirmation. The browser decides when a save request occurs; generation does not depend on that callback.

The service does not retrieve emails/OTP, submit forms, solve CAPTCHA, use accessibility scraping or create overlays. Keyboard inline suggestions, passkeys, cross-domain credential sharing and multi-step username-only flows are not implemented. Dropdown-based Autofill and manual copy/paste are the supported paths.

## Build

Requires Android 11+ with strong enrolled biometrics. The standalone Kotlin/Compose project is tracked under `native/`. There is no Expo prebuild step.

```sh
./build-apk.sh --check
./build-apk.sh
```

The script selects JDK 17 (including Fedora's installed Temurin), checks Android SDK 36 and uses Gradle 8.13. It creates/reuses a unique local signing key. No NDK, CMake, Node, Expo or cloud account is needed. Initial dependency downloads require Internet access.

On success, send `artifacts/clave.apk` with LocalSend and install it. Preserve `.signing/` securely for updates. This is not a security-audited production password manager.

## Device acceptance matrix — pending

Use fictional credentials. Serve fixtures/autofill.html over HTTPS with a device-trusted certificate. No hosting/deployment was performed. The fixture discards inputs, navigating without sending passwords. Repeat against a real disposable test account; a fixture does not establish browser-wide compatibility.

| Scenario                                     | Expected                                                              |
| -------------------------------------------- | --------------------------------------------------------------------- |
| Missing strong biometrics                    | Refuse vault access                                                   |
| Cold launch / return from background         | Logo and one unlock button; one automatic biometric prompt            |
| Cancel automatic prompt / press Back         | Remain locked without error dialog or automatic retry; button retries |
| Tap Bloquear while in app                    | Remain locked until button is tapped or app is reopened                |
| Cancel setup, unlock or target confirmation  | No unlocked session or credential release                             |
| Create vault, restart app                    | Persistent data, biometric unlock required                            |
| Enable Clave in Android settings             | Setup card reports enabled                                            |
| New-password form                            | Clave opens authenticated picker                                      |
| Generate in Ocio                             | Saved before filling; new/confirmation fields filled                  |
| Current + new-password form                  | Generation leaves current password untouched                          |
| Website rejects an update                    | Previous password recoverable from pending detail                     |
| Login on exact saved origin                  | Accounts grouped by profile; selection fills form                     |
| Manually enter and submit credentials        | Android save prompt and profile confirmation when browser supports it |
| Existing username/profile/destination        | Explicit update confirmation                                          |
| HTTP, mixed origin, different domain         | Rejected or no matching credentials                                   |
| Different app signing certificate            | Native app credential not offered                                     |
| Background picker or wait one minute         | Close session and clear key                                           |
| Background main app or two-minute inactivity | Lock and clear visible state                                          |
| Concurrent app edit and browser save         | Reject stale revision; unlock to refresh                              |
| Process dies before picker opens             | Expired request; no false save success                                |
| Change biometrics on disposable vault        | Key may invalidate; never overwrite existing vault                    |

## Verification boundary

The six vault/autofill Kotlin files and document model compile directly against Android API 36. Twelve JVM tests cover matching, generation, crypto and immutable document/profile/pending-password operations. Toolchain discovery and shell syntax pass. Full Compose/Gradle APK assembly, manifest merge, R8, Keystore behavior, installation and this device matrix remain unverified: the assistant environment cannot write the Gradle home or resolve dependency-download hosts. No native APK was produced here.
