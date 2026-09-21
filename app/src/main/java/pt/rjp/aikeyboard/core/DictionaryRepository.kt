package pt.rjp.aikeyboard.core

import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class DictionaryRepository(private val context: Context) {
    private val personal = PersonalDictionary(context)
    private val lexicons = ConcurrentHashMap<Language, Lexicon>()
    private val bigrams = ConcurrentHashMap<Language, Map<String, Int>>()

    fun get(language: Language): Lexicon = lexicons.getOrPut(language) { loadLexicon(language) }
    fun bigrams(language: Language): Map<String, Int> = bigrams.getOrPut(language) { loadBigrams(language) }

    fun invalidate(language: Language) {
        lexicons.remove(language)
    }

    fun invalidateAll() {
        lexicons.clear()
    }

    fun learn(language: Language, word: String) {
        personal.learn(language, word)
        invalidate(language)
    }

    private fun loadLexicon(language: Language): Lexicon {
        val entries = ArrayList<WordEntry>()
        val update = DictionaryUpdateManager.dictionaryFile(context, language)

        // Prefer the downloaded dictionary, but never let a damaged runtime
        // update make the IME unusable. Delete it and fall back to the APK copy.
        val loadedUpdate = if (update.isFile && update.length() > 0L) {
            try {
                update.bufferedReader(Charsets.UTF_8).use { readEntries(it, entries) }
                entries.isNotEmpty()
            } catch (t: Throwable) {
                Log.w("RjpDictionary", "Updated dictionary invalid for ${language.tag}; using bundled copy", t)
                entries.clear()
                try { update.delete() } catch (_: Throwable) {}
                false
            }
        } else false

        if (!loadedUpdate) {
            context.assets.open("dictionaries/${language.dictionaryAsset}").bufferedReader(Charsets.UTF_8).use {
                readEntries(it, entries)
            }
        }

        personal.words(language).forEach { (word, count) ->
            entries.add(WordEntry(word, 500_000 + count * 1000))
        }
        return Lexicon(entries, Locale.forLanguageTag(language.tag))
    }

    private fun readEntries(reader: java.io.BufferedReader, entries: MutableList<WordEntry>) {
        reader.lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val parts = line.split('\t')
            val word = parts.firstOrNull()?.trim().orEmpty()
            val freq = parts.getOrNull(1)?.toIntOrNull() ?: 100
            if (word.length >= 2) entries.add(WordEntry(word, freq.coerceAtLeast(1)))
        }
    }

    private fun loadBigrams(language: Language): Map<String, Int> {
        val result = HashMap<String, Int>()
        try {
            context.assets.open("bigrams/${language.bigramAsset}").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                    val p = line.split('\t')
                    if (p.size >= 3) result["${p[0].lowercase()}\t${p[1].lowercase()}"] = p[2].toIntOrNull() ?: 1
                }
            }
        } catch (t: Throwable) {
            Log.w("RjpDictionary", "Bigram dictionary unavailable for ${language.tag}", t)
        }
        return result
    }
}
