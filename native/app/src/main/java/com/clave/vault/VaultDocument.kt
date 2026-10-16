package com.clave.vault

import com.clave.autofill.CredentialRules
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class Credential(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val profile: String = "Personal",
    val appPackage: String = "",
    val appSignature: String = "",
    val status: String = "saved",
    val previousPassword: String? = null,
    val updatedAt: String = Instant.now().toString(),
    val accent: AccountAccent? = null, // Legacy metadata only; account colors always follow their profile.
) {
    fun toJson() = JSONObject().put("id", id).put("title", title).put("username", username)
        .put("password", password).put("website", website).put("profile", profile)
        .put("appPackage", appPackage).put("appSignature", appSignature).put("status", status)
        .put("previousPassword", previousPassword).put("updatedAt", updatedAt).put("accent", accent?.name)

    companion object {
        fun fromJson(json: JSONObject) = Credential(
            id = json.getString("id"), title = json.getString("title"),
            username = json.getString("username"), password = json.getString("password"),
            website = json.optString("website"), profile = json.getString("profile"),
            appPackage = json.optString("appPackage"), appSignature = json.optString("appSignature"),
            status = json.optString("status", "saved"),
            previousPassword = if (json.has("previousPassword") && !json.isNull("previousPassword")) json.getString("previousPassword") else null,
            updatedAt = json.optString("updatedAt"),
            accent = AccountAccent.fromStored(json.optString("accent")),
        )
    }
}

/** Immutable UI snapshot. Failed writes never mutate the open session or displayed entries. */
data class VaultDocument(val revision: Long, val profiles: List<String>, val credentials: List<Credential>,
                         val profileColors: Map<String, AccountAccent> = emptyMap()) {
    fun toJson() = JSONObject().put("version", 1).put("revision", revision)
        .put("profiles", JSONArray(profiles)).put("credentials", JSONArray(credentials.map { it.toJson() }))
        .put("profileColors", JSONObject(profileColors.mapValues { it.value.name }))

    fun profileColor(name: String) = profileColors[name] ?: AccountAccent.forProfile(name)
    fun accountColor(entry: Credential) = profileColor(entry.profile)
    fun setProfileColor(name: String, color: AccountAccent): VaultDocument {
        require(name in profiles) { "El perfil ya no existe." }
        return copy(profileColors = profileColors + (name to color))
    }

    fun upsert(entry: Credential): VaultDocument {
        require(entry.title.length <= 100) { "El nombre puede tener hasta 100 caracteres." }
        require(entry.username.isNotBlank() && entry.username.length <= 250) { "Introduce un usuario válido (máximo 250 caracteres)." }
        require(entry.password.isNotEmpty() && entry.password.length <= 1024) { "Introduce una contraseña (máximo 1024 caracteres)." }
        require(entry.profile in profiles) { "Elige un perfil existente." }
        require(entry.website.isBlank() || CredentialRules.origin(entry.website) != null) { "Usa una dirección HTTPS válida sin usuario ni puerto especial." }
        // A display label is required on disk, but users need not invent one.
        val displayTitle = entry.title.trim().ifEmpty {
            (CredentialRules.origin(entry.website)?.removePrefix("https://")
                ?: entry.appPackage.takeIf { it.isNotBlank() } ?: entry.username.trim()).take(100)
        }
        val old = credentials.firstOrNull { it.id == entry.id }
        // Editing a pending password must not silently discard the recoverable previous value.
        val updated = entry.copy(title = displayTitle, updatedAt = Instant.now().toString(),
            previousPassword = if (entry.status == "pending") old?.previousPassword ?: entry.previousPassword else null)
        return copy(credentials = if (old == null) credentials + updated else credentials.map { if (it.id == updated.id) updated else it })
    }

    fun delete(id: String) = copy(credentials = credentials.filterNot { it.id == id })

    fun confirm(id: String) = copy(credentials = credentials.map {
        if (it.id == id) it.copy(status = "saved", previousPassword = null) else it
    })

    fun restore(id: String): VaultDocument {
        val entry = credentials.first { it.id == id }
        require(entry.previousPassword != null) { "No hay una contraseña anterior guardada." }
        return upsert(entry.copy(password = entry.previousPassword, previousPassword = null, status = "saved"))
    }

    fun addProfile(input: String): VaultDocument {
        val name = input.trim()
        require(name.isNotEmpty() && name.length <= 30) { "El perfil debe tener entre 1 y 30 caracteres." }
        require(profiles.size < 100) { "Máximo 100 perfiles." }
        require(profiles.none { it.equals(name, true) }) { "Ese perfil ya existe." }
        return copy(profiles = profiles + name)
    }

    fun renameProfile(old: String, input: String): VaultDocument {
        val name = input.trim()
        require(old in profiles)
        require(name.isNotEmpty() && name.length <= 30) { "El perfil debe tener entre 1 y 30 caracteres." }
        require(profiles.none { it != old && it.equals(name, true) }) { "Ese perfil ya existe." }
        return copy(profiles = profiles.map { if (it == old) name else it },
            profileColors = (profileColors - old) + (name to profileColor(old)),
            credentials = credentials.map { if (it.profile == old) it.copy(profile = name) else it })
    }

    fun deleteProfile(name: String): VaultDocument {
        require(profiles.size > 1) { "Conserva al menos un perfil." }
        require(credentials.none { it.profile == name }) { "Mueve o elimina las cuentas de este perfil primero." }
        return copy(profiles = profiles.filterNot { it == name }, profileColors = profileColors - name)
    }

    companion object {
        fun fromJson(json: JSONObject): VaultDocument {
            require(json.getInt("version") == 1)
            val profiles = json.getJSONArray("profiles")
            val entries = json.getJSONArray("credentials")
            val colors = json.optJSONObject("profileColors")
            val names = (0 until profiles.length()).map { profiles.getString(it) }
            return VaultDocument(json.getLong("revision"),
                names, (0 until entries.length()).map { Credential.fromJson(entries.getJSONObject(it)) },
                names.mapNotNull { name -> AccountAccent.fromStored(colors?.optString(name))?.let { name to it } }.toMap())
        }
    }
}
