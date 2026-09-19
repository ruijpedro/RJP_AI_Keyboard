package pt.rjp.aikeyboard.core

import android.content.Context
import java.io.File
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
        val reader = if (update.isFile && update.length() > 0L) {
            update.bufferedReader(Charsets.UTF_8)
        } else {
            context.assets.open("dictionaries/${language.dictionaryAsset}").bufferedReader()
        }

        reader.useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val parts = line.split('\t')
                val word = parts[0].trim()
                val freq = parts.getOrNull(1)?.toIntOrNull() ?: 100
                if (word.length >= 2) entries.add(WordEntry(word, freq))
            }
        }
        personal.words(language).forEach { (word, count) ->
            entries.add(WordEntry(word, 500_000 + count * 1000))
        }
        return Lexicon(entries, Locale.forLanguageTag(language.tag))
    }

    private fun loadBigrams(language: Language): Map<String, Int> {
        val result = HashMap<String, Int>()
        context.assets.open("bigrams/${language.bigramAsset}").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val p = line.split('\t')
                if (p.size >= 3) result["${p[0].lowercase()}\t${p[1].lowercase()}"] = p[2].toIntOrNull() ?: 1
            }
        }
        return result
    }
}
