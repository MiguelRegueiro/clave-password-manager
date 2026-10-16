import test from "node:test";
import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import {
  GROUPS,
  generatePassword,
  randomIndex,
  normalizeWebsite,
} from "../src/password.mjs";

test("generator satisfies length and every selected character group", () => {
  for (const length of [12, 20, 64])
    for (const symbols of [true, false]) {
      for (let i = 0; i < 50; i++) {
        const password = generatePassword(length, symbols, randomBytes);
        assert.equal(password.length, length);
        for (const group of GROUPS.slice(0, symbols ? 4 : 3))
          assert.ok([...password].some((c) => group.includes(c)));
        if (!symbols) assert.match(password, /^[a-zA-Z0-9]+$/);
      }
    }
});
test("generator rejects invalid lengths", () => {
  for (const length of [0, 11, 65, 12.5, NaN])
    assert.throws(() => generatePassword(length, true, randomBytes));
});
test("random index rejects biased tail", () => {
  const bytes = [255, 254, 7];
  assert.equal(
    randomIndex(10, () => [bytes.shift()]),
    7,
  );
});
test("website normalization only accepts HTTPS without credentials", () => {
  assert.equal(normalizeWebsite(" example.com/login "), "https://example.com");
  assert.equal(normalizeWebsite(""), "");
  for (const url of [
    "http://example.com",
    "javascript:alert(1)",
    "https://user:pass@example.com",
    "not a domain",
  ]) {
    assert.throws(() => normalizeWebsite(url));
  }
});
