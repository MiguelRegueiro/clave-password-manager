package com.clave.autofill

import org.junit.Assert.*
import org.junit.Test

class CredentialRulesTest {
  @Test fun exactHttpsOriginsOnly() {
    assertEquals("https://example.com", CredentialRules.origin("https://EXAMPLE.com/login"))
    for (url in listOf("http://example.com", "https://user:pass@example.com", "https://example.com:8443", "javascript:alert(1)")) assertNull(CredentialRules.origin(url))
    assertFalse(CredentialRules.matches("https://example.com", "https://example.com.evil.test", "", "", "browser", "cert"))
    assertFalse(CredentialRules.matches("https://example.com", "https://login.example.com", "", "", "browser", "cert"))
    assertTrue(CredentialRules.matches("https://example.com/path", "https://example.com", "", "", "browser", "cert"))
  }
  @Test fun nativeAppsRequirePinnedPackageAndCertificate() {
    assertTrue(CredentialRules.matches("", null, "app", "abc", "app", "abc"))
    assertFalse(CredentialRules.matches("", null, "app", "abc", "app", "other"))
    assertFalse(CredentialRules.matches("", null, "app", "abc", "fake", "abc"))
    assertFalse(CredentialRules.matches("", null, "app", "", "app", ""))
    assertFalse(CredentialRules.matches("https://example.com", null, "app", "abc", "app", "abc"))
  }
  @Test fun passwordsMeetRequestedConstraints() {
    for (size in listOf(12, 20, 64)) for (symbols in listOf(true, false)) repeat(100) {
      val password = CredentialRules.password(size, symbols)
      assertEquals(size, password.length)
      assertTrue(password.any { it.isUpperCase() }); assertTrue(password.any { it.isLowerCase() }); assertTrue(password.any { it.isDigit() })
      assertEquals(symbols, password.any { !it.isLetterOrDigit() })
    }
  }
}
