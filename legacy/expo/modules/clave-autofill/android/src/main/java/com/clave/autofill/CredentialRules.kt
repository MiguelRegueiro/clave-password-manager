package com.clave.autofill

import java.net.IDN
import java.net.URI
import java.security.SecureRandom

/** Exact origins only: no suffix matches, HTTP downgrade or credential-bearing URLs. */
object CredentialRules {
  fun origin(input: String): String? = try {
    val uri = URI(input)
    if (uri.scheme != "https" || uri.rawUserInfo != null || uri.host.isNullOrBlank() || uri.port !in listOf(-1, 443)) null
    else "https://" + IDN.toASCII(uri.host).lowercase().removeSuffix(".")
  } catch (_: Exception) { null }

  fun matches(website: String, sourceOrigin: String?, savedPackage: String, savedSignature: String,
    sourcePackage: String, sourceSignature: String): Boolean {
    if (sourceOrigin != null) return origin(website) == sourceOrigin
    return website.isEmpty() && savedPackage == sourcePackage && savedSignature.isNotEmpty() && savedSignature == sourceSignature
  }

  fun password(length: Int = 20, symbols: Boolean = true): String {
    require(length in 12..64)
    val random = SecureRandom()
    val groups = mutableListOf("ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnopqrstuvwxyz", "23456789")
    if (symbols) groups.add("!@#$%&*+-=?")
    val alphabet = groups.joinToString("")
    val chars = groups.map { it[random.nextInt(it.length)] }.toMutableList()
    while (chars.size < length) chars.add(alphabet[random.nextInt(alphabet.length)])
    for (i in chars.lastIndex downTo 1) {
      val j = random.nextInt(i + 1)
      val temp = chars[i]; chars[i] = chars[j]; chars[j] = temp
    }
    return chars.joinToString("")
  }
}
