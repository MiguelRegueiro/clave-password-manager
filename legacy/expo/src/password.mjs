export const GROUPS = [
  "ABCDEFGHJKLMNPQRSTUVWXYZ",
  "abcdefghijkmnopqrstuvwxyz",
  "23456789",
  "!@#$%&*+-=?",
];

// Rejection sampling avoids modulo bias. The caller supplies OS random bytes.
export function randomIndex(max, randomBytes) {
  const ceiling = 256 - (256 % max);
  let byte;
  do {
    byte = randomBytes(1)[0];
  } while (byte >= ceiling);
  return byte % max;
}

export function generatePassword(length, symbols, randomBytes) {
  if (!Number.isInteger(length) || length < 12 || length > 64)
    throw new Error("Longitud inválida.");
  const groups = symbols ? GROUPS : GROUPS.slice(0, 3);
  const alphabet = groups.join("");
  const result = groups.map(
    (group) => group[randomIndex(group.length, randomBytes)],
  );
  while (result.length < length)
    result.push(alphabet[randomIndex(alphabet.length, randomBytes)]);
  for (let i = result.length - 1; i > 0; i--) {
    const j = randomIndex(i + 1, randomBytes);
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result.join("");
}

export function normalizeWebsite(input) {
  const raw = input.trim();
  if (!raw) return "";
  const url = new URL(raw.includes("://") ? raw : `https://${raw}`);
  if (
    url.protocol !== "https:" ||
    !url.hostname.includes(".") ||
    url.username ||
    url.password
  ) {
    throw new Error("Introduce una dirección web HTTPS válida.");
  }
  return url.origin;
}
