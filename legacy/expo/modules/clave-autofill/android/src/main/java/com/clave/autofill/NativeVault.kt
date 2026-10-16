package com.clave.autofill

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom

/** One shared encrypted vault for React Native and the system autofill activities.
 * The RSA private wrapping key requires a fresh biometric CryptoObject operation.
 * No background service can decrypt the vault or cache an unlocked key.
 */
object NativeVault {
  private const val ALIAS = "clave.autofill.wrapping.v1"
  private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "clave-vault-v1.json"))
  fun exists(context: Context) = file(context).baseFile.exists()
  private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
  private fun un64(value: String) = Base64.decode(value, Base64.NO_WRAP)
  private fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  private fun empty() = JSONObject().put("version", 1).put("revision", 0)
    .put("profiles", JSONArray(listOf("Ocio", "Trabajo", "Personal"))).put("credentials", JSONArray())

  private fun createWrappingKey() {
    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
      initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
        .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
        .setUserAuthenticationRequired(true)
        .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        .setInvalidatedByBiometricEnrollment(true).build())
      generateKeyPair()
    }
  }

  class Session internal constructor(val context: Context, val vault: JSONObject,
    private val key: ByteArray, private val wrapped: ByteArray) : AutoCloseable {
    private var closed = false
    fun save(next: JSONObject): JSONObject = synchronized(NativeVault) {
      check(!closed) { "La sesión ha caducado." }
      validate(next)
      val disk = if (exists(context)) decrypt(JSONObject(String(file(context).readFully())), key) else null
      val revision = disk?.getLong("revision") ?: 0L
      check(next.optLong("revision", -1) == revision) {
        "La bóveda cambió desde otra pantalla. Bloquea y vuelve a abrir para actualizarla."
      }
      next.put("revision", revision + 1)
      val plain = next.toString().toByteArray()
      val encrypted = try { VaultCrypto.encrypt(plain, key) } finally { plain.fill(0) }
      val envelope = JSONObject().put("format", 1).put("wrapped", b64(wrapped))
        .put("nonce", b64(encrypted.nonce)).put("data", b64(encrypted.ciphertext)).toString().toByteArray()
      val target = file(context)
      val stream = target.startWrite()
      try { stream.write(envelope); target.finishWrite(stream) }
      catch (error: Exception) { target.failWrite(stream); throw error }
      next
    }
    override fun close() { closed = true; key.fill(0) }
  }

  private fun validate(vault: JSONObject) {
    require(vault.getInt("version") == 1)
    val profiles = vault.getJSONArray("profiles")
    require(profiles.length() in 1..100) { "Se permiten entre 1 y 100 perfiles." }
    val names = (0 until profiles.length()).map { profiles.getString(it) }
    require(names.all { it.isNotBlank() && it.length <= 30 } && names.distinctBy { it.lowercase() }.size == names.size)
    val entries = vault.getJSONArray("credentials")
    require(entries.length() <= 10000)
    val ids = mutableSetOf<String>()
    for (i in 0 until entries.length()) {
      val item = entries.getJSONObject(i)
      require(item.getString("id").isNotBlank() && ids.add(item.getString("id")))
      require(item.getString("title").isNotBlank() && item.getString("title").length <= 100)
      require(item.getString("username").isNotBlank() && item.getString("username").length <= 250)
      require(item.getString("password").isNotEmpty() && item.getString("password").length <= 1024)
      require(item.getString("profile") in names)
    }
  }
  private fun decrypt(envelope: JSONObject, key: ByteArray): JSONObject {
    require(envelope.getInt("format") == 1) { "Formato de bóveda no compatible." }
    val plain = VaultCrypto.decrypt(VaultCrypto.Block(un64(envelope.getString("nonce")), un64(envelope.getString("data"))), key)
    return try { JSONObject(String(plain)).also { validate(it) } } finally { plain.fill(0) }
  }

  fun unlock(activity: Activity, create: Boolean = false, success: (Session) -> Unit,
    failure: (String) -> Unit): CancellationSignal {
    val cancellation = CancellationSignal()
    var delivered = false
    fun fail(message: String) { if (!delivered) { delivered = true; failure(message) } }
    try {
      require(activity.getSystemService(BiometricManager::class.java)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
        "Configura una huella o biometría fuerte en los ajustes del dispositivo."
      }
      val existing = exists(activity)
      require(existing || create) { "Abre Clave y crea tu bóveda antes de usar el autocompletado." }
      val envelope = if (existing) JSONObject(String(file(activity).readFully())) else null
      if (!existing) createWrappingKey()
      val keyStore = keys()
      val wrapped = if (envelope != null) un64(envelope.getString("wrapped")) else {
        val dataKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try { VaultCrypto.wrap(dataKey, keyStore.getCertificate(ALIAS).publicKey) } finally { dataKey.fill(0) }
      }
      val cipher = VaultCrypto.unwrapCipher(keyStore.getKey(ALIAS, null))
      BiometricPrompt.Builder(activity).setTitle("Desbloquear Clave")
        .setSubtitle("Acceso a tu bóveda de contraseñas")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButton("Cancelar", activity.mainExecutor) { _, _ -> fail("Autenticación cancelada.") }
        .build().authenticate(BiometricPrompt.CryptoObject(cipher), cancellation, activity.mainExecutor,
          object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(code: Int, message: CharSequence) { fail("No se pudo autenticar: $message") }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
              if (delivered) return
              if (activity.isFinishing || activity.isDestroyed || cancellation.isCanceled) { fail("La sesión ha terminado."); return }
              var key: ByteArray? = null
              try {
                key = result.cryptoObject!!.cipher!!.doFinal(wrapped)
                val data = if (envelope == null) empty() else decrypt(envelope, key)
                val session = Session(activity.applicationContext, data, key, wrapped)
                if (envelope == null) session.save(data)
                delivered = true
                success(session)
              } catch (error: Exception) { key?.fill(0); fail("No se pudo abrir la bóveda. La clave puede haber sido invalidada o los datos están dañados.") }
            }
          })
    } catch (error: Exception) { fail(error.message ?: "No se pudo abrir la bóveda.") }
    return cancellation
  }
}
