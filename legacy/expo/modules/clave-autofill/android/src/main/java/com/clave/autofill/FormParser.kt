package com.clave.autofill

import android.app.assist.AssistStructure
import android.content.Context
import android.content.pm.PackageManager
import android.text.InputType
import android.view.autofill.AutofillId
import java.security.MessageDigest

data class Field(val id: AutofillId, val value: String, val newPassword: Boolean = false)
data class Form(val sourcePackage: String, val signature: String, val origin: String?,
  val username: Field?, val passwords: List<Field>) {
  val target get() = origin ?: sourcePackage
  val ids get() = (listOfNotNull(username?.id) + passwords.map { it.id }).toTypedArray()
  val newFields get() = passwords.filter { it.newPassword }.ifEmpty { passwords }
  val submittedPassword get() = newFields.firstOrNull { it.value.isNotEmpty() }?.value ?: ""
}

object FormParser {
  @Suppress("DEPRECATION")
  fun parse(context: Context, structure: AssistStructure): Form? {
    val source = structure.activityComponent?.packageName ?: return null
    if (source == context.packageName) return null
    val signature = try {
      val info = context.packageManager.getPackageInfo(source, PackageManager.GET_SIGNING_CERTIFICATES)
      info.signingInfo?.apkContentsSigners?.map { signer ->
        MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it) }
      }?.sorted()?.joinToString(":") ?: ""
    } catch (_: Exception) { "" }
    val domains = mutableSetOf<String>()
    val users = mutableListOf<Field>()
    val passwords = mutableListOf<Field>()
    val fieldDomains = mutableListOf<String?>()
    var unsafe = false
    var count = 0
    fun visit(node: AssistStructure.ViewNode, inherited: String?) {
      if (++count > 5000) { unsafe = true; return }
      var domain = inherited
      node.webDomain?.let {
        // Never guess HTTPS when the browser does not provide the scheme.
        val candidate = CredentialRules.origin("${node.webScheme}://$it")
        if (candidate == null) unsafe = true else { domains.add(candidate); domain = candidate }
      }
      val attributes = node.htmlInfo?.attributes?.associate { it.first.lowercase() to it.second.lowercase() } ?: emptyMap()
      val hints = node.autofillHints?.joinToString(" ")?.lowercase().orEmpty() + " " + attributes["autocomplete"].orEmpty()
      val type = attributes["type"].orEmpty()
      val variation = node.inputType and InputType.TYPE_MASK_VARIATION
      val password = hints.contains("password") || type == "password" ||
        variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
        (node.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
      val name = attributes["name"] ?: attributes["id"] ?: node.idEntry?.lowercase().orEmpty()
      val user = hints.contains("username") || hints.contains("email") || type == "email" || name in listOf("username", "email", "login", "user", "identifier")
      val id = node.autofillId
      if (id != null && (password || user)) {
        // Do not mix fields from a native wrapper with an embedded web origin.
        if (node.htmlInfo != null && domain == null) unsafe = true
        val value = node.autofillValue?.takeIf { it.isText }?.textValue?.toString().orEmpty()
        val field = Field(id, value, hints.contains("new-password") || hints.contains("newpassword") ||
          name in listOf("new_password", "newpassword", "confirm_password", "password_confirmation", "confirmpassword"))
        fieldDomains.add(domain)
        if (password) passwords.add(field) else users.add(field)
      }
      for (i in 0 until node.childCount) visit(node.getChildAt(i), domain)
    }
    for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode, null)
    if (unsafe || domains.size > 1 || passwords.isEmpty() || passwords.size > 3 || users.size > 1) return null
    // Ambiguous current/new/confirmation fields must never receive the same generated secret.
    if (passwords.size > 1 && passwords.none { it.newPassword }) return null
    if (domains.isNotEmpty() && fieldDomains.any { it != domains.first() }) return null
    if (domains.isEmpty() && signature.isEmpty()) return null
    return Form(source, signature, domains.firstOrNull(), users.firstOrNull(), passwords)
  }
}
