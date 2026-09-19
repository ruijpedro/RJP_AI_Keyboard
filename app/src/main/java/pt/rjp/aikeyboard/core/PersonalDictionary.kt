package pt.rjp.aikeyboard.core

import android.content.Context
import java.util.Locale

class PersonalDictionary(context: Context) {
    private val prefs = context.getSharedPreferences("rjp_personal_dictionary", Context.MODE_PRIVATE)

    fun words(language: Language): Map<String, Int> {
        val prefix = "${language.tag}|"
        return prefs.all.entries.asSequence()
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { entry ->
                val value = entry.value as? Int ?: return@mapNotNull null
                entry.key.removePrefix(prefix) to value
            }.toMap()
    }

    fun learn(language: Language, rawWord: String) {
        val word = rawWord.trim().lowercase(Locale.forLanguageTag(language.tag))
        if (word.length < 2 || word.any { it.isDigit() }) return
        val key = "${language.tag}|$word"
        val old = prefs.getInt(key, 0)
        prefs.edit().putInt(key, (old + 1).coerceAtMost(1000)).apply()
    }

    fun remove(language: Language, rawWord: String) {
        val word = rawWord.trim().lowercase(Locale.forLanguageTag(language.tag))
        prefs.edit().remove("${language.tag}|$word").apply()
    }

    fun clear(language: Language) {
        val edit = prefs.edit()
        val prefix = "${language.tag}|"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(edit::remove)
        edit.apply()
    }
}
