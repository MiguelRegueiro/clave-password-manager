package com.clave.autofill

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import com.clave.vault.ThemeSettings
import com.clave.vault.R
import android.graphics.Color
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Android-owned autofill authentication/save surface; no React runtime is needed. */
class AutofillActivity : Activity() {
  override fun attachBaseContext(base: Context) { super.attachBaseContext(ThemeSettings.themedContext(base)) }
  private var session: NativeVault.Session? = null
  private var cancellation: CancellationSignal? = null
  private lateinit var request: Requests.Request
  private lateinit var body: LinearLayout
  private val handler = Handler(Looper.getMainLooper())
  private val expire = Runnable { finish() }
  private var completed = false

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    setResult(RESULT_CANCELED)
    if (state != null) { finish(); return }
    val pending = Requests.take(intent.getStringExtra("request"))
    if (pending == null) { message("La solicitud ha caducado. Vuelve al formulario y selecciona Clave otra vez."); return }
    request = pending
    layout("Desbloquear Clave")
    text("${request.form.target}\nSolicitado por ${request.form.sourcePackage}")
    cancellation = NativeVault.unlock(this, success = { unlocked ->
      if (isFinishing) { unlocked.close(); return@unlock }
      session = unlocked
      handler.postDelayed(expire, 60000)
      confirmSource()
    }, failure = { message(it) })
  }

  private fun confirmSource() {
    val form = request.form
    if (form.origin != null) {
      // A package name alone cannot prove that a browser/WebView owns the reported domain.
      // Android's recommended fallback is explicit confirmation of the unverified association.
      AlertDialog.Builder(this).setTitle("Comprueba el sitio web")
        .setMessage("${form.sourcePackage} solicita contraseñas para:\n\n${form.origin}\n\nClave no ha verificado la relación entre esta app y el dominio. Continúa solo si estás en ese sitio dentro de un navegador de confianza.")
        .setPositiveButton("Confirmar sitio") { _, _ -> showContent() }
        .setNegativeButton("Cancelar") { _, _ -> finish() }
        .setOnCancelListener { finish() }.show()
    } else showContent()
  }
  private fun showContent() { if (request.save) editor(false) else picker() }
  private fun layout(title: String) {
    body = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(28, 40, 28, 32)
      setBackgroundColor(getColor(R.color.clave_background))
      importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
      setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        val padding = (20 * resources.displayMetrics.density).toInt()
        view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
        insets
      }
    }
    setContentView(ScrollView(this).apply { isFillViewport = true; addView(body) })
    body.requestApplyInsets()
    text("CLAVE", 13)
    text(title, 25)
  }
  private fun text(value: String, size: Int = 16) = TextView(this).also {
    it.text = value; it.textSize = size.toFloat(); it.setTextColor(getColor(R.color.clave_text)); it.setPadding(0, 10, 0, 12)
    body.addView(it)
  }
  private fun button(label: String, action: () -> Unit) = Button(this).also {
    it.text = label; it.isAllCaps = false; body.addView(it)
    it.setOnClickListener { if (!completed && session != null) action() }
  }
  private fun field(label: String, value: String, secret: Boolean = false) = EditText(this).also {
    text(label, 13)
    it.setSingleLine(true); it.isSaveEnabled = false
    it.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    it.inputType = InputType.TYPE_CLASS_TEXT or (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    it.setText(value); body.addView(it)
  }
  private fun matches(item: JSONObject): Boolean = CredentialRules.matches(item.optString("website"), request.form.origin,
    item.optString("appPackage"), item.optString("appSignature"), request.form.sourcePackage, request.form.signature)

  private fun picker() {
    layout("Iniciar sesión")
    text(request.form.target)
    val vault = session!!.vault
    val entries = vault.getJSONArray("credentials")
    var count = 0
    val profiles = vault.getJSONArray("profiles")
    for (p in 0 until profiles.length()) {
      val profile = profiles.getString(p)
      val matching = (0 until entries.length()).map { entries.getJSONObject(it) }.filter { matches(it) && it.getString("profile") == profile }
      if (matching.isNotEmpty()) {
        text(profile, 18)
        for (entry in matching) {
          count++
          button("${entry.getString("title")} · ${entry.getString("username")}") {
            AlertDialog.Builder(this).setTitle("Rellenar esta cuenta")
              .setMessage("${entry.getString("username")}\nPerfil: $profile\nDestino: ${request.form.target}")
              .setPositiveButton("Rellenar") { _, _ -> fill(entry, false) }
              .setNegativeButton("Cancelar", null).show()
          }
        }
      }
    }
    if (count == 0) text("No hay cuentas guardadas para este destino.")
    button("Generar y guardar una contraseña") { editor(true) }
    button("Cancelar") { finish() }
  }

  private fun editor(generate: Boolean) {
    layout(if (generate) "Crear contraseña" else "Guardar contraseña")
    text(request.form.target)
    if (generate) text("Se guardará antes de rellenar para que no la pierdas. Confirma después el cambio en la web; Clave no cambia tu contraseña por sí sola.", 14)
    val username = field("Usuario o correo", request.form.username?.value.orEmpty())
    val password = field("Contraseña", if (generate) CredentialRules.password() else request.form.submittedPassword, true)
    if (generate) {
      val length = field("Longitud (12–64)", "20").apply { inputType = InputType.TYPE_CLASS_NUMBER }
      val symbols = CheckBox(this).apply { text = "Incluir símbolos"; isChecked = true; body.addView(this) }
      button("Volver a generar") {
        val size = length.text.toString().toIntOrNull()
        if (size == null || size !in 12..64) length.error = "Elige entre 12 y 64 caracteres."
        else password.setText(CredentialRules.password(size, symbols.isChecked))
      }
    }
    text("Guardar en el perfil", 13)
    val profiles = session!!.vault.getJSONArray("profiles")
    val names = (0 until profiles.length()).map { profiles.getString(it) }
    val spinner = Spinner(this).apply {
      adapter = ArrayAdapter(this@AutofillActivity, android.R.layout.simple_spinner_dropdown_item, names)
      body.addView(this)
    }
    val newProfile = field("O crear un perfil (opcional)", "")
    button(if (generate) "Guardar y rellenar" else "Guardar cuenta") {
      val user = username.text.toString().trim()
      val secret = password.text.toString()
      val requestedProfile = newProfile.text.toString().trim().ifEmpty { spinner.selectedItem.toString() }
      val profile = names.firstOrNull { it.equals(requestedProfile, true) } ?: requestedProfile
      if (user.isEmpty() || user.length > 250) { username.error = "Introduce un usuario (máximo 250 caracteres)."; return@button }
      if (secret.isEmpty() || secret.length > 1024) { password.error = "Introduce una contraseña válida."; return@button }
      if (profile.length > 30 || profile.equals("Todos", true)) { newProfile.error = "Elige otro nombre (máximo 30 caracteres)."; return@button }
      val entries = session!!.vault.getJSONArray("credentials")
      val existing = (0 until entries.length()).map { entries.getJSONObject(it) }
        .firstOrNull { matches(it) && it.getString("username") == user && it.getString("profile") == profile }
      val commit = {
        try {
          val vault = session!!.vault
          if (profile !in names) vault.getJSONArray("profiles").put(profile)
          val entry = existing ?: JSONObject().put("id", UUID.randomUUID().toString())
          if (generate && existing != null && existing.optString("status") != "pending") entry.put("previousPassword", existing.getString("password"))
          if (!generate) entry.remove("previousPassword")
          entry.put("title", (request.form.origin?.removePrefix("https://") ?: request.form.sourcePackage).take(100))
            .put("website", request.form.origin ?: "").put("username", user).put("password", secret).put("profile", profile)
            .put("appPackage", request.form.sourcePackage).put("appSignature", request.form.signature)
            .put("updatedAt", Instant.now().toString()).put("status", if (generate) "pending" else "saved")
          if (existing == null) entries.put(entry)
          session!!.save(vault)
          if (generate) fill(entry, true) else { completed = true; setResult(RESULT_OK); finish() }
        } catch (error: Exception) { message(error.message ?: "No se pudo guardar. Vuelve a abrir Clave.") }
      }
      if (existing != null) AlertDialog.Builder(this).setTitle("Actualizar contraseña")
        .setMessage("Ya existe $user en $profile para ${request.form.target}. ¿Sustituir la contraseña guardada?")
        .setPositiveButton("Actualizar") { _, _ -> commit() }.setNegativeButton("Cancelar", null).show()
      else commit()
    }
    button("Cancelar") { finish() }
  }

  private fun fill(entry: JSONObject, generated: Boolean) {
    val form = request.form
    val label = presentation(this, "${entry.getString("profile")} · ${entry.getString("username")}")
    val dataset = Dataset.Builder(label)
    form.username?.let { dataset.setValue(it.id, AutofillValue.forText(entry.getString("username"))) }
    val fields = if (generated) form.newFields else form.passwords.filterNot { it.newPassword }
    if (fields.isEmpty()) { message("Este formulario pide una contraseña nueva. Usa Generar en lugar de una cuenta existente."); return }
    for (field in fields) dataset.setValue(field.id, AutofillValue.forText(entry.getString("password")))
    val response = FillResponse.Builder().addDataset(dataset.build()).setSaveInfo(saveInfo(form)).build()
    completed = true
    setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response))
    finish()
  }
  private fun message(value: String) {
    if (isFinishing) return
    AlertDialog.Builder(this).setTitle("Clave").setMessage(value)
      .setPositiveButton("Cerrar") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
  }
  override fun onStop() {
    super.onStop()
    cancellation?.cancel()
    session?.close(); session = null
    finish()
  }
  override fun onDestroy() {
    handler.removeCallbacks(expire)
    session?.close(); session = null
    super.onDestroy()
  }
}
