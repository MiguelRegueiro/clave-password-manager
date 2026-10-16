package com.clave.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.Settings
import android.view.View
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.clave.autofill.NativeVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var session: NativeVault.Session? = null
    private var cancellation: CancellationSignal? = null
    private var document by mutableStateOf<VaultDocument?>(null)
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var exists by mutableStateOf(false)
    private var autofillEnabled by mutableStateOf(false)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var screen by mutableStateOf("vault")
    private var selected by mutableStateOf<String?>(null)
    private var draft by mutableStateOf<Credential?>(null)
    private var epoch = 0
    // Arm only after a real background/start transition, not every biometric resume.
    private var autoUnlockPending = true
    private val handler = Handler(Looper.getMainLooper())
    private val idle = Runnable { lock() }
    private val hideDetail = Runnable { selected = null; draft = null }
    private var clipboardToken: String? = null
    private val clearClip = Runnable { clearOwnClipboard() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        enableEdgeToEdge()
        exists = NativeVault.exists(this)
        themeMode = ThemeSettings.read(this)
        setContent {
            ClaveTheme(themeMode) {
                ClaveApp(
                    document = document, busy = busy, error = error, notice = notice,
                    screen = screen, selected = selected, draft = draft, autofillEnabled = autofillEnabled,
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it; ThemeSettings.save(this, it) },
                    onUnlock = { unlock() }, onLock = ::lock,
                    onScreen = { screen = it; selected = null; draft = null },
                    onSelect = { id -> unlock(id) },
                    onNew = { password, profile -> draft = Credential(password = password, profile = profile ?: document!!.profiles.first()); selected = null },
                    onEdit = { entry -> draft = entry; selected = null; handler.removeCallbacks(hideDetail) },
                    onBack = { draft = null; selected = null },
                    onSave = { entry -> mutate { it.upsert(entry) } },
                    onDelete = { id -> mutate { it.delete(id) } },
                    onConfirm = { id -> mutate { it.confirm(id) } },
                    onRestore = { id -> mutate { it.restore(id) } },
                    onSaveProfile = { old, name, color -> mutate(closeDetail = false) {
                        (if (old == null) it.addProfile(name) else it.renameProfile(old, name)).setProfileColor(name.trim(), color)
                    } },
                    onDeleteProfile = { name -> mutate(closeDetail = false) { it.deleteProfile(name) } },
                    onCopy = ::copySecret, onEnableAutofill = ::enableAutofill,
                    onDismissError = { error = null }, onDismissNotice = { notice = null },
                )
            }
        }
    }

    private fun unlock(id: String? = null) {
        if (busy) return
        busy = true
        error = null
        val generation = epoch
        cancellation = NativeVault.unlock(this, create = !exists, success = { fresh ->
            if (epoch != generation || isFinishing) { fresh.close(); return@unlock }
            session?.close()
            session = fresh
            document = VaultDocument.fromJson(fresh.vault)
            exists = true
            busy = false
            selected = id?.takeIf { key -> document!!.credentials.any { it.id == key } }
            resetIdle()
            handler.removeCallbacks(hideDetail)
            if (selected != null) handler.postDelayed(hideDetail, 30_000)
        }, failure = {
            if (epoch == generation) { busy = false; error = it }
        }, cancelled = {
            if (epoch == generation) { busy = false; error = null }
        })
    }

    private fun mutate(closeDetail: Boolean = true, transform: (VaultDocument) -> VaultDocument) {
        if (busy) return
        val active = session ?: return
        val snapshot = document ?: return
        val next = try { transform(snapshot) } catch (e: IllegalArgumentException) {
            error = e.message ?: "Revisa los datos."; return
        }
        busy = true
        val generation = epoch
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { VaultDocument.fromJson(active.save(next.toJson())) }
                if (epoch == generation) {
                    document = saved
                    if (closeDetail) { selected = null; draft = null }
                    notice = "Guardado en tu dispositivo."
                }
            } catch (_: Exception) {
                if (epoch == generation) {
                    lock()
                    error = "No se pudo guardar. Desbloquea de nuevo: la bóveda puede haber cambiado desde el autocompletado."
                }
            } finally { if (epoch == generation) busy = false }
        }
    }

    private fun lock() {
        epoch++
        cancellation?.cancel(); cancellation = null
        session?.close(); session = null
        document = null; selected = null; draft = null; busy = false; notice = null
        handler.removeCallbacks(idle); handler.removeCallbacks(hideDetail)
    }

    private fun resetIdle() {
        handler.removeCallbacks(idle)
        if (document != null) handler.postDelayed(idle, 120_000)
    }

    override fun onUserInteraction() { super.onUserInteraction(); resetIdle() }

    private fun copySecret(value: String) {
        if (session == null) return
        val token = "clave-${UUID.randomUUID()}"
        val clip = ClipData.newPlainText(token, value)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        clipboardToken = token
        handler.removeCallbacks(clearClip); handler.postDelayed(clearClip, 30_000)
        notice = "Copiado. Se intentará borrar del portapapeles en 30 segundos."
    }

    private fun clearOwnClipboard() {
        val manager = getSystemService(ClipboardManager::class.java)
        if (clipboardToken != null && manager.primaryClipDescription?.label?.toString() == clipboardToken) manager.clearPrimaryClip()
        clipboardToken = null
    }

    private fun enableAutofill() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:$packageName")))
        } catch (_: Exception) { error = "Abre Ajustes → Contraseñas y autocompletado y selecciona Clave." }
    }

    override fun onResume() {
        super.onResume()
        exists = NativeVault.exists(this)
        autofillEnabled = getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
        if (autoUnlockPending) {
            autoUnlockPending = false
            val generation = epoch
            // Let the logo render first, and never launch a queued prompt after backgrounding.
            handler.post {
                if (epoch == generation && document == null && !busy &&
                    lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) unlock()
            }
        }
    }

    override fun onStop() { autoUnlockPending = true; lock(); super.onStop() }
    override fun onDestroy() {
        lock()
        clearOwnClipboard()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
