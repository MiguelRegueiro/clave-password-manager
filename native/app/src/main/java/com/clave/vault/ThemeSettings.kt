package com.clave.vault

import android.content.Context
import android.content.res.Configuration

object ThemeSettings {
    private fun preferences(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun read(context: Context) = ThemeMode.fromStored(preferences(context).getString("theme", null))
    fun save(context: Context, mode: ThemeMode) { preferences(context).edit().putString("theme", mode.name).apply() }

    // Native autofill widgets/dialogs must use the same preference as the Compose app.
    fun themedContext(context: Context): Context {
        val mode = read(context)
        if (mode == ThemeMode.SYSTEM) return context
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (mode == ThemeMode.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return context.createConfigurationContext(config)
    }
}
