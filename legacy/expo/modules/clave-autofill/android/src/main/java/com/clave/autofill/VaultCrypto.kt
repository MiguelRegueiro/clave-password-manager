package com.clave.autofill

import java.security.Key
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** Standard JCA primitives shared by the Android vault and host-side tests. */
object VaultCrypto {
  data class Block(val nonce: ByteArray, val ciphertext: ByteArray)
  private val aad = "clave.native.v1".toByteArray()
  private val padding = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
  fun encrypt(plaintext: ByteArray, key: ByteArray): Block {
    require(key.size == 32)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
    cipher.updateAAD(aad)
    return Block(cipher.iv, cipher.doFinal(plaintext))
  }
  fun decrypt(block: Block, key: ByteArray): ByteArray {
    require(key.size == 32 && block.nonce.size == 12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, block.nonce))
    cipher.updateAAD(aad)
    return cipher.doFinal(block.ciphertext)
  }
  fun wrap(key: ByteArray, publicKey: Key): ByteArray {
    require(key.size == 32)
    val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey, padding)
    return cipher.doFinal(key)
  }
  fun unwrapCipher(privateKey: Key): Cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").apply {
    init(Cipher.DECRYPT_MODE, privateKey, padding)
  }
}
