package com.clave.autofill

import android.app.PendingIntent
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.CancellationSignal
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import java.util.UUID

/** Short-lived, one-use handles. Secrets/structures are never put in launch intents or logs. */
object Requests {
  data class Request(val form: Form, val save: Boolean, val time: Long = SystemClock.elapsedRealtime())
  private val requests = mutableMapOf<String, Request>()
  private val expiry = Handler(Looper.getMainLooper())
  @Synchronized fun sender(context: Context, form: Form, save: Boolean): IntentSender {
    requests.entries.removeAll { SystemClock.elapsedRealtime() - it.value.time > 120000 }
    if (requests.size >= 20) requests.remove(requests.keys.first())
    val token = UUID.randomUUID().toString()
    requests[token] = Request(form, save)
    expiry.postDelayed({ synchronized(this) { requests.remove(token) } }, 120000)
    val intent = Intent(context, AutofillActivity::class.java).putExtra("request", token)
    // The user launches this one-use intent from Android's Autofill UI while Clave
    // is in the background. Target SDK 35+ requires creator-side launch opt-in.
    val options = ActivityOptions.makeBasic()
    if (Build.VERSION.SDK_INT >= 35) options.setPendingIntentCreatorBackgroundActivityStartMode(
      ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
    return PendingIntent.getActivity(context, token.hashCode(), intent,
      PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE, options.toBundle()).intentSender
  }
  @Synchronized fun take(token: String?): Request? {
    val request = requests.remove(token) ?: return null
    return request.takeIf { SystemClock.elapsedRealtime() - it.time <= 120000 }
  }
}

fun presentation(context: Context, label: String) = RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
  setTextViewText(android.R.id.text1, label)
}
fun saveInfo(form: Form): SaveInfo = SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD,
  arrayOf(form.newFields.first().id)).apply {
    form.username?.let { setOptionalIds(arrayOf(it.id)) }
    setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
    setDescription("Guardar en Clave para ${form.target}. Confirma el destino y el perfil antes de guardar.")
  }.build()

class ClaveAutofillService : AutofillService() {
  override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
    try {
      val structure = request.fillContexts.lastOrNull()?.structure
      val form = structure?.let { FormParser.parse(this, it) }
      if (cancellationSignal.isCanceled) return
      if (form == null) { callback.onSuccess(null); return }
      // No usernames or passwords leave storage before authentication and target confirmation.
      callback.onSuccess(FillResponse.Builder()
        .setAuthentication(form.ids, Requests.sender(this, form, false), presentation(this, "Clave · Elegir cuenta o generar contraseña"))
        .setSaveInfo(saveInfo(form)).build())
    } catch (_: Exception) { callback.onSuccess(null) }
  }
  override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
    try {
      val form = request.fillContexts.lastOrNull()?.structure?.let { FormParser.parse(this, it) }
      if (form == null || form.submittedPassword.isEmpty()) { callback.onFailure("No se pudo reconocer la contraseña de este formulario."); return }
      callback.onSuccess(Requests.sender(this, form, true))
    } catch (_: Exception) { callback.onFailure("No se pudo abrir el guardado. Vuelve a intentarlo desde Clave.") }
  }
}
