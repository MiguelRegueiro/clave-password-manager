package com.clave.autofill

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.os.CancellationSignal
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject

class ClaveModule : Module() {
  private var pending = false
  private var authentication: CancellationSignal? = null
  override fun definition() = ModuleDefinition {
    Name("ClaveAutofill")
    OnUserLeavesActivity { authentication?.cancel() }
    OnActivityDestroys { authentication?.cancel() }
    OnDestroy { authentication?.cancel() }
    AsyncFunction("hasVault") { NativeVault.exists(requireNotNull(appContext.reactContext)) }
    AsyncFunction("isEnabled") {
      appContext.reactContext?.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() ?: false
    }
    AsyncFunction("enable") {
      val context = requireNotNull(appContext.reactContext)
      context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
        Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    AsyncFunction("open") { create: Boolean, promise: Promise -> access(create, null, promise) }
    AsyncFunction("save") { json: String, promise: Promise -> access(false, json, promise) }
  }
  private fun access(create: Boolean, json: String?, promise: Promise) {
    val activity = appContext.currentActivity
    if (activity == null) { promise.reject("NO_ACTIVITY", "Abre la aplicación para autenticarte.", null); return }
    activity.runOnUiThread {
      if (pending) { promise.reject("BUSY", "Ya hay una autenticación en curso.", null); return@runOnUiThread }
      pending = true
      authentication = NativeVault.unlock(activity, create, { session ->
        try {
          val result = if (json == null) session.vault else session.save(JSONObject(json))
          promise.resolve(result.toString())
        } catch (error: Exception) { promise.reject("VAULT_ERROR", error.message, null) }
        finally { session.close(); pending = false }
      }, { message -> pending = false; promise.reject("AUTH_ERROR", message, null) })
    }
  }
}
