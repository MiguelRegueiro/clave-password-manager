import test from "node:test";
import assert from "node:assert/strict";
import { randomBytes, createDecipheriv } from "node:crypto";
import { encryptVault, decryptVault } from "../src/encryption.mjs";

const vault = {
  version: 1,
  profiles: ["Ocio"],
  credentials: [
    { title: "Ejemplo", username: "ana", password: "Una contraseña única 🔑" },
  ],
};
test("encrypted vault round trips Unicode without storing plaintext", () => {
  const key = randomBytes(32);
  const raw = encryptVault(vault, key, randomBytes(12));
  assert.deepEqual(decryptVault(raw, key), vault);
  assert.ok(!raw.includes("Ejemplo"));
  assert.ok(!raw.includes("contraseña"));
});
test("AES-256-GCM envelope is compatible with independent Node implementation", () => {
  const key = randomBytes(32);
  const envelope = JSON.parse(encryptVault(vault, key, randomBytes(12)));
  const ciphertext = Buffer.from(envelope.data, "hex");
  const decipher = createDecipheriv(
    "aes-256-gcm",
    key,
    Buffer.from(envelope.nonce, "hex"),
  );
  decipher.setAAD(Buffer.from("clave.vault.v1"));
  decipher.setAuthTag(ciphertext.subarray(-16));
  const result = Buffer.concat([
    decipher.update(ciphertext.subarray(0, -16)),
    decipher.final(),
  ]);
  assert.deepEqual(JSON.parse(result.toString("utf8")), vault);
});
test("tampering and an incorrect key are rejected", () => {
  const key = randomBytes(32);
  const raw = encryptVault(vault, key, randomBytes(12));
  assert.throws(() => decryptVault(raw, randomBytes(32)));
  for (const field of ["data", "nonce"]) {
    const envelope = JSON.parse(raw);
    envelope[field] =
      (envelope[field][0] === "0" ? "1" : "0") + envelope[field].slice(1);
    assert.throws(() => decryptVault(JSON.stringify(envelope), key));
  }
});
test("fresh nonces produce different ciphertext for identical vaults", () => {
  const key = randomBytes(32);
  assert.notEqual(
    encryptVault(vault, key, randomBytes(12)),
    encryptVault(vault, key, randomBytes(12)),
  );
});
