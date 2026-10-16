import { gcm } from "@noble/ciphers/aes";
import {
  bytesToHex,
  hexToBytes,
  utf8ToBytes,
  bytesToUtf8,
} from "@noble/ciphers/utils";

const aad = utf8ToBytes("clave.vault.v1");

export function encryptVault(vault, key, nonce) {
  if (key.length !== 32 || nonce.length !== 12)
    throw new Error("Parámetros de cifrado inválidos.");
  const plaintext = utf8ToBytes(JSON.stringify(vault));
  try {
    return JSON.stringify({
      version: 1,
      nonce: bytesToHex(nonce),
      data: bytesToHex(gcm(key, nonce, aad).encrypt(plaintext)),
    });
  } finally {
    plaintext.fill(0);
  }
}

export function decryptVault(raw, key) {
  const envelope = JSON.parse(raw);
  if (envelope.version !== 1)
    throw new Error("Formato de bóveda no compatible.");
  const nonce = hexToBytes(envelope.nonce);
  if (key.length !== 32 || nonce.length !== 12)
    throw new Error("Parámetros de cifrado inválidos.");
  const plaintext = gcm(key, nonce, aad).decrypt(hexToBytes(envelope.data));
  try {
    const vault = JSON.parse(bytesToUtf8(plaintext));
    if (
      vault.version !== 1 ||
      !Array.isArray(vault.profiles) ||
      !Array.isArray(vault.credentials)
    )
      throw new Error("Bóveda inválida.");
    return vault;
  } finally {
    plaintext.fill(0);
  }
}
