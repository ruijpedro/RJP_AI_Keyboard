package pt.rjp.aikeyboard.core

import android.content.Context

class KeyboardPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("rjp_keyboard", Context.MODE_PRIVATE)

    var language: Language
        get() = Language.fromTag(prefs.getString("language", Language.PT_PT.tag))
        set(value) = prefs.edit().putString("language", value.tag).apply()

    var autoCorrect: Boolean
        get() = prefs.getBoolean("auto_correct", true)
        set(value) = prefs.edit().putBoolean("auto_correct", value).apply()

    var autoCapitalize: Boolean
        get() = prefs.getBoolean("auto_capitalize", true)
        set(value) = prefs.edit().putBoolean("auto_capitalize", value).apply()

    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(value) = prefs.edit().putBoolean("vibration", value).apply()

    var privateMode: Boolean
        get() = prefs.getBoolean("private_mode", true)
        set(value) = prefs.edit().putBoolean("private_mode", value).apply()

    var aiEndpoint: String
        get() = prefs.getString("ai_endpoint", "") ?: ""
        set(value) = prefs.edit().putString("ai_endpoint", value.trim()).apply()

    var aiEnabled: Boolean
        get() = prefs.getBoolean("ai_enabled", false)
        set(value) = prefs.edit().putBoolean("ai_enabled", value).apply()

    var smartContext: Boolean
        get() = prefs.getBoolean("smart_context", true)
        set(value) = prefs.edit().putBoolean("smart_context", value).apply()
}
