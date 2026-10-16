import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";
import * as LocalAuthentication from "expo-local-authentication";
import * as Crypto from "expo-crypto";
import Constants, { ExecutionEnvironment } from "expo-constants";
import { Platform } from "react-native";
import { androidVault } from "./autofill";
import { bytesToHex, hexToBytes } from "@noble/ciphers/utils";
import { encryptVault, decryptVault } from "./encryption.mjs";

export type Credential = {
  id: string;
  title: string;
  website: string;
  username: string;
  password: string;
  profile: string;
  updatedAt: string;
  appPackage?: string;
  appSignature?: string;
  status?: "pending" | "saved";
  previousPassword?: string;
};
export type Vault = {
  version: 1;
  profiles: string[];
  credentials: Credential[];
  revision?: number;
};
const STORAGE = "clave.encrypted-vault.v1";
const KEY = "clave.vault-key.v1";
// Expo Go cannot load the native password-manager integration.
export const isExpoGo =
  Constants.executionEnvironment === ExecutionEnvironment.StoreClient;
function requireInstalledApp() {
  if (isExpoGo)
    throw new Error(
      "Instala Clave para usar la bóveda. Expo Go no es compatible.",
    );
}
const options: SecureStore.SecureStoreOptions = {
  requireAuthentication: true,
  authenticationPrompt: "Desbloquea tus contraseñas",
  keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
};

export async function hasVault() {
  requireInstalledApp();
  if (Platform.OS === "android") {
    if (!androidVault)
      throw new Error(
        "Instala la nueva compilación Android de Clave para usar el autocompletado.",
      );
    return androidVault.hasVault();
  }
  return (await AsyncStorage.getItem(STORAGE)) !== null;
}

export async function checkBiometrics() {
  if (
    !(await SecureStore.isAvailableAsync()) ||
    !(await LocalAuthentication.hasHardwareAsync()) ||
    !(await LocalAuthentication.isEnrolledAsync()) ||
    (await LocalAuthentication.getEnrolledLevelAsync()) !==
      LocalAuthentication.SecurityLevel.BIOMETRIC_STRONG
  ) {
    throw new Error(
      "Configura una huella o Face ID seguro en los ajustes del dispositivo para usar Clave.",
    );
  }
}

export async function createVault() {
  requireInstalledApp();
  if (Platform.OS === "android") {
    if (!androidVault) throw new Error("Recompila Clave para Android.");
    // Do not overwrite or silently abandon an older JavaScript-managed vault.
    if (await AsyncStorage.getItem(STORAGE))
      throw new Error(
        "Existe una bóveda de la versión anterior. Esta actualización requiere una migración antes de crear otra bóveda. Tus datos no se han modificado.",
      );
    return JSON.parse(await androidVault.open(true)) as Vault;
  }
  await checkBiometrics();
  if (await hasVault())
    throw new Error("Ya existe una bóveda. Desbloquéala para continuar.");
  const result = await LocalAuthentication.authenticateAsync({
    promptMessage: "Crea tu bóveda privada",
    disableDeviceFallback: true,
    biometricsSecurityLevel: "strong",
  });
  if (!result.success) throw new Error("No se ha completado la autenticación.");
  const key = Crypto.getRandomBytes(32);
  try {
    await SecureStore.setItemAsync(KEY, bytesToHex(key), options);
    const vault: Vault = {
      version: 1,
      profiles: ["Ocio", "Trabajo", "Personal"],
      credentials: [],
    };
    await writeVault(vault, key);
    return vault;
  } finally {
    key.fill(0);
  }
}

export async function readKey() {
  requireInstalledApp();
  await checkBiometrics();
  const value = await SecureStore.getItemAsync(KEY, options);
  if (!value)
    throw new Error(
      "La clave ya no está disponible. Cambiar la biometría o reinstalar la app puede impedir recuperar esta bóveda.",
    );
  return hexToBytes(value);
}

export async function openVault(): Promise<Vault> {
  requireInstalledApp();
  if (Platform.OS === "android") {
    if (!androidVault) throw new Error("Recompila Clave para Android.");
    return JSON.parse(await androidVault.open(false)) as Vault;
  }
  const key = await readKey();
  try {
    const raw = await AsyncStorage.getItem(STORAGE);
    if (!raw) throw new Error("No se encuentra la bóveda.");
    return decryptVault(raw, key);
  } finally {
    key.fill(0);
  }
}

async function writeVault(vault: Vault, key: Uint8Array) {
  const nonce = Crypto.getRandomBytes(12);
  await AsyncStorage.setItem(STORAGE, encryptVault(vault, key, nonce));
}

export async function saveVault(vault: Vault) {
  requireInstalledApp();
  if (Platform.OS === "android") {
    if (!androidVault) throw new Error("Recompila Clave para Android.");
    const saved = JSON.parse(
      await androidVault.save(JSON.stringify(vault)),
    ) as Vault;
    vault.revision = saved.revision;
    return;
  }
  const key = await readKey();
  try {
    await writeVault(vault, key);
  } finally {
    key.fill(0);
  }
}
