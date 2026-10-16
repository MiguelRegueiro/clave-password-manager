// Compatibility alias. The actual build needs only Bash/Java/Gradle.
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
const result = spawnSync("bash", [fileURLToPath(new URL("../build-apk.sh", import.meta.url)), ...process.argv.slice(2)], { stdio: "inherit" });
if (result.error) console.error(result.error.message);
process.exit(result.status ?? 1);
