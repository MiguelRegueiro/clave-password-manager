package com.clave.autofill

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.SecureRandom

class VaultCryptoTest {
  private fun key() = ByteArray(32).also { SecureRandom().nextBytes(it) }
  @Test fun roundTripAndFreshNonces() {
    val key = key()
    val plain = "contraseña de prueba 🔑".toByteArray()
    val a = VaultCrypto.encrypt(plain, key)
    val b = VaultCrypto.encrypt(plain, key)
    assertArrayEquals(plain, VaultCrypto.decrypt(a, key))
    assertFalse(a.nonce.contentEquals(b.nonce))
    assertFalse(a.ciphertext.contentEquals(b.ciphertext))
  }
  @Test fun rejectsTamperingAndWrongKeys() {
    val key = key()
    val block = VaultCrypto.encrypt("secret".toByteArray(), key)
    assertThrows(Exception::class.java) { VaultCrypto.decrypt(block, key()) }
    val changed = block.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
    assertThrows(Exception::class.java) { VaultCrypto.decrypt(VaultCrypto.Block(block.nonce, changed), key) }
    val nonce = block.nonce.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
    assertThrows(Exception::class.java) { VaultCrypto.decrypt(VaultCrypto.Block(nonce, block.ciphertext), key) }
  }
  @Test fun rsaWrappedKeyRoundTrip() {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val key = key()
    assertArrayEquals(key, VaultCrypto.unwrapCipher(pair.private).doFinal(VaultCrypto.wrap(key, pair.public)))
  }
  @Test fun rejectsInvalidKeyAndNonceLengths() {
    assertThrows(IllegalArgumentException::class.java) { VaultCrypto.encrypt(byteArrayOf(1), ByteArray(16)) }
    assertThrows(IllegalArgumentException::class.java) { VaultCrypto.decrypt(VaultCrypto.Block(ByteArray(8), ByteArray(16)), key()) }
  }
}
