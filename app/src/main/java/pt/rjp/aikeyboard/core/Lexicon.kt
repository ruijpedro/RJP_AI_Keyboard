package pt.rjp.aikeyboard.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class WordEntry(val word: String, val frequency: Int)
data class Suggestion(val word: String, val score: Double, val distance: Int)

class Lexicon(entries: List<WordEntry>, private val locale: Locale) {
    companion object {
        private const val MAX_FUZZY_WORDS = 30_000
        private const val PREFIX_DEPTH = 4
        private const val PREFIX_KEEP = 30
    }

    private val sortedEntries = entries
        .distinctBy { normalize(it.word) }
        .sortedByDescending { it.frequency }

    private val exact = HashMap<String, WordEntry>(sortedEntries.size * 2)
    private val prefix = HashMap<String, MutableList<WordEntry>>()
    private val deleteIndex = HashMap<String, MutableList<WordEntry>>()

    init {
        sortedEntries.forEach { e ->
            val n = normalize(e.word)
            exact[n] = e
            for (i in 1..minOf(PREFIX_DEPTH, n.length)) {
                val p = n.substring(0, i)
                val bucket = prefix.getOrPut(p) { ArrayList(PREFIX_KEEP) }
                if (bucket.size < PREFIX_KEEP) bucket.add(e)
            }
        }
        sortedEntries.take(MAX_FUZZY_WORDS).forEach { e ->
            val n = normalize(e.word)
            if (n.length in 3..24) {
                generateDeletes(n, 2).forEach { d ->
                    val bucket = deleteIndex.getOrPut(d) { ArrayList(4) }
                    if (bucket.size < 12) bucket.add(e)
                }
            }
        }
    }

    fun contains(word: String): Boolean = exact.containsKey(normalize(word))

    fun suggestions(rawWord: String, previousWord: String?, bigrams: Map<String, Int>, limit: Int = 3): List<Suggestion> {
        val word = normalize(rawWord)
        if (word.isBlank()) return emptyList()

        val candidateMap = LinkedHashMap<String, WordEntry>()
        exact[word]?.let { candidateMap[it.word] = it }

        val p = word.take(minOf(PREFIX_DEPTH, word.length))
        prefix[p].orEmpty().forEach { candidateMap[it.word] = it }

        generateDeletes(word, 2).forEach { key ->
            deleteIndex[key].orEmpty().forEach { candidateMap[it.word] = it }
        }

        // Length-near exact prefix fallback avoids scanning the entire dictionary.
        if (candidateMap.size < 12 && word.length >= 2) {
            prefix[word.take(1)].orEmpty().forEach { e ->
                if (abs(e.word.length - rawWord.length) <= 2) candidateMap[e.word] = e
            }
        }

        return candidateMap.values.asSequence()
            .map { entry ->
                val candidateNorm = normalize(entry.word)
                val distance = damerauLevenshtein(word, candidateNorm, 2)
                val isPrefix = candidateNorm.startsWith(word)
                if (distance > 2 && !isPrefix) return@map null
                val prev = previousWord?.let(::normalize).orEmpty()
                val bigramBoost = if (prev.isNotEmpty()) bigrams["$prev\t$candidateNorm"] ?: 0 else 0
                val prefixBoost = if (isPrefix) 90.0 else 0.0
                val exactBoost = if (candidateNorm == word) 400.0 else 0.0
                val score = exactBoost + prefixBoost + entry.frequency.coerceAtMost(1_000_000) / 1000.0 + bigramBoost * 8.0 - distance * 140.0
                Suggestion(entry.word, score, distance)
            }
            .filterNotNull()
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.distance }.thenBy { it.word.length })
            .distinctBy { normalize(it.word) }
            .take(limit)
            .toList()
    }

    private fun normalize(s: String): String = s.trim().lowercase(locale)

    private fun generateDeletes(word: String, maxDistance: Int): Set<String> {
        if (word.isEmpty()) return emptySet()
        var current = setOf(word)
        val all = LinkedHashSet<String>()
        repeat(maxDistance) {
            val next = LinkedHashSet<String>()
            current.forEach { w ->
                if (w.length > 1) {
                    for (i in w.indices) {
                        val d = w.removeRange(i, i + 1)
                        if (all.add(d)) next.add(d)
                    }
                }
            }
            current = next
        }
        return all
    }

    private fun damerauLevenshtein(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (abs(a.length - b.length) > max) return max + 1
        val prevPrev = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, prevPrev[j - 2] + 1)
                }
                cur[j] = v
                rowMin = minOf(rowMin, v)
            }
            if (rowMin > max) return max + 1
            for (j in prev.indices) prevPrev[j] = prev[j]
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }
}
