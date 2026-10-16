import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, copyFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { homedir } from "node:os";

const root = fileURLToPath(new URL("../", import.meta.url));
if (process.argv.includes("--help")) {
  console.log(
    "Build a standalone Android testing APK with the local Android SDK/JDK.\nRuns Expo prebuild, then Gradle assembleRelease.\nOutput: artifacts/clave-preview.apk\nNo Expo account, Metro server or connected phone required.\nThe generated template uses a debug signing key unless you configure release signing. Do not publish this as a production build.",
  );
  process.exit(0);
}
if (process.platform === "win32") {
  console.error(
    "Use npm run apk for a cloud build, or run this local build script in Linux/macOS.",
  );
  process.exit(1);
}
const env = { ...process.env };
function children(directory) {
  if (!existsSync(directory)) return [];
  return readdirSync(directory).map((name) => path.join(directory, name));
}
function isJdk17(directory) {
  if (!directory || !existsSync(path.join(directory, "bin/javac"))) return false;
  const result = spawnSync(path.join(directory, "bin/java"), ["-version"], {
    encoding: "utf8",
  });
  return result.status === 0 && /version "17[."]/.test(result.stderr + result.stdout);
}
const javaHome = [
  env.CLAVE_JAVA_HOME,
  env.JAVA_HOME,
  path.join(root, ".toolchains/jdk-17"),
  ...children("/usr/lib/jvm"),
  ...children(path.join(homedir(), ".jdks")),
  ...children(path.join(homedir(), ".sdkman/candidates/java")),
  ...children("/Library/Java/JavaVirtualMachines").map((dir) => path.join(dir, "Contents/Home")),
].find(isJdk17);
if (!javaHome || (env.CLAVE_JAVA_HOME && !isJdk17(env.CLAVE_JAVA_HOME))) {
  console.error(
    "JDK 17 is required for this Android build. Java 25/27 can break Prefab/CMake.\nInstall a Temurin 17 JDK from https://adoptium.net/temurin/releases/?version=17 then set CLAVE_JAVA_HOME to the extracted JDK directory.\nExample: CLAVE_JAVA_HOME=/path/to/jdk-17 npm run apk:local\nNo system Java change or Expo account is required.",
  );
  process.exit(1);
}
env.JAVA_HOME = javaHome;
env.PATH = `${path.join(javaHome, "bin")}${path.delimiter}${env.PATH || ""}`;
env.NODE_ENV = "production";
console.log(`Java 17: ${javaHome}`);
// Preserve explicit configuration; discover Android Studio's default SDK otherwise.
if (
  !env.ANDROID_HOME &&
  !env.ANDROID_SDK_ROOT &&
  !existsSync(path.join(root, "android/local.properties"))
) {
  const sdk = [
    path.join(homedir(), "Android/Sdk"),
    path.join(homedir(), "Library/Android/sdk"),
  ].find((candidate) =>
    ["platforms", "build-tools", "platform-tools"].every((directory) =>
      existsSync(path.join(candidate, directory)),
    ),
  );
  if (sdk) env.ANDROID_HOME = sdk;
}
if (
  !env.ANDROID_HOME &&
  !env.ANDROID_SDK_ROOT &&
  !existsSync(path.join(root, "android/local.properties"))
) {
  console.error(
    "Android SDK not found. Install it using Android Studio's SDK Manager, then set ANDROID_HOME to its location.",
  );
  process.exit(1);
}
console.log(
  `Android SDK: ${env.ANDROID_HOME || env.ANDROID_SDK_ROOT || "android/local.properties"}`,
);
if (process.argv.includes("--check")) {
  console.log("SDK configuration found. No build was started.");
  process.exit(0);
}
function run(command, args, cwd = root) {
  const result = spawnSync(command, args, { cwd, env, stdio: "inherit" });
  if (result.error) console.error(result.error.message);
  if (result.error || result.status !== 0) {
    console.error(
      "APK build did not complete. No new APK has been produced by this run.",
    );
    process.exit(result.status || 1);
  }
}
run("npx", ["expo", "prebuild", "--platform", "android", "--no-install"]);
run(
  "./gradlew",
  [":app:assembleRelease", "--no-daemon", `-Dorg.gradle.java.home=${javaHome}`],
  path.join(root, "android"),
);
const built = path.join(
  root,
  "android/app/build/outputs/apk/release/app-release.apk",
);
if (!existsSync(built)) {
  console.error(
    "Gradle completed but app-release.apk was not found. Inspect android/app/build/outputs/apk/release.",
  );
  process.exit(1);
}
const target = path.join(root, "artifacts/clave-preview.apk");
mkdirSync(path.dirname(target), { recursive: true });
copyFileSync(built, target);
console.log(
  `\nAPK ready: ${target}\nInstall it directly on Android; Expo Go and Metro are not required.\nFor distribution, use your release signing configuration or npm run apk (EAS managed signing).`,
);
