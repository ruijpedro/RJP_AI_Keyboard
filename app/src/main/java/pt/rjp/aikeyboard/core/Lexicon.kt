package pt.rjp.aikeyboard.core

import java.util.Locale
import kotlin.math.abs

data class WordEntry(val word: String, val frequency: Int)
data class Suggestion(val word: String, val score: Double, val distance: Int)

/**
 * Memory-conscious lexicon for an Android IME.
 *
 * The old implementation created delete signatures up to edit distance 2 for
 * 30k words. With full Hunspell dictionaries that can create well over a
 * million temporary/index strings and may exhaust the Android app heap.
 *
 * This version keeps the complete exact-word map, a small prefix index, a
 * distance-1 delete index for the most useful words and a bounded length
 * fallback. The final Damerau-Levenshtein check still accepts distance <= 2.
 */
class Lexicon(entries: List<WordEntry>, private val locale: Locale) {
    companion object {
        private const val PREFIX_DEPTH = 4
        private const val PREFIX_KEEP = 32
        private const val MAX_DELETE_INDEX_WORDS = 12_000
        private const val MAX_DELETE_BUCKET = 10
        private const val LENGTH_BUCKET_KEEP = 450
        private const val MAX_CANDIDATES = 180
    }

    private val sortedEntries = entries
        .asSequence()
        .filter { it.word.length >= 2 }
        .distinctBy { normalize(it.word) }
        .sortedByDescending { it.frequency }
        .toList()

    private val exact = HashMap<String, WordEntry>((sortedEntries.size * 1.4).toInt().coerceAtLeast(16))
    private val prefix = HashMap<String, MutableList<WordEntry>>()
    private val deleteIndex = HashMap<String, MutableList<WordEntry>>()
    private val lengthBuckets = HashMap<Int, MutableList<WordEntry>>()

    init {
        sortedEntries.forEach { e ->
            val n = normalize(e.word)
            if (n.isBlank()) return@forEach
            exact[n] = e

            for (i in 1..minOf(PREFIX_DEPTH, n.length)) {
                val p = n.substring(0, i)
                val bucket = prefix.getOrPut(p) { ArrayList(PREFIX_KEEP) }
                if (bucket.size < PREFIX_KEEP) bucket.add(e)
            }

            val lengthBucket = lengthBuckets.getOrPut(n.length) { ArrayList(LENGTH_BUCKET_KEEP) }
            if (lengthBucket.size < LENGTH_BUCKET_KEEP) lengthBucket.add(e)
        }

        // A distance-1 delete index is enough to cheaply discover the vast
        // majority of one/two-edit candidates while keeping memory bounded.
        sortedEntries.take(MAX_DELETE_INDEX_WORDS).forEach { e ->
            val n = normalize(e.word)
            if (n.length in 3..24) {
                generateDeletes1(n).forEach { d ->
                    val bucket = deleteIndex.getOrPut(d) { ArrayList(4) }
                    if (bucket.size < MAX_DELETE_BUCKET) bucket.add(e)
                }
            }
        }
    }

    fun contains(word: String): Boolean = exact.containsKey(normalize(word))

    fun suggestions(rawWord: String, previousWord: String?, bigrams: Map<String, Int>, limit: Int = 3): List<Suggestion> {
        val word = normalize(rawWord)
        if (word.isBlank()) return emptyList()

        val candidateMap = LinkedHashMap<String, WordEntry>(64)
        exact[word]?.let { candidateMap[it.word] = it }

        val p = word.take(minOf(PREFIX_DEPTH, word.length))
        prefix[p].orEmpty().forEach { candidateMap.putIfRoom(it) }

        // Query deletes matched against dictionary deletes discover insertion,
        // deletion and substitution candidates without a huge distance-2 map.
        generateDeletes1(word).forEach { key ->
            deleteIndex[key].orEmpty().forEach { candidateMap.putIfRoom(it) }
        }
        deleteIndex[word].orEmpty().forEach { candidateMap.putIfRoom(it) }

        // Bounded fallback for genuine distance-2 errors not found above.
        if (candidateMap.size < 18) {
            val first = word.firstOrNull()
            for (len in (word.length - 2).coerceAtLeast(2)..(word.length + 2)) {
                lengthBuckets[len].orEmpty().forEach { e ->
                    if (candidateMap.size >= MAX_CANDIDATES) return@forEach
                    val n = normalize(e.word)
                    if (first == null || n.firstOrNull() == first || word.length <= 4) {
                        candidateMap[e.word] = e
                    }
                }
            }
        }

        val prev = previousWord?.let(::normalize).orEmpty()
        return candidateMap.values.asSequence()
            .mapNotNull { entry ->
                val candidateNorm = normalize(entry.word)
                val distance = damerauLevenshtein(word, candidateNorm, 2)
                val isPrefix = candidateNorm.startsWith(word)
                if (distance > 2 && !isPrefix) return@mapNotNull null
                val bigramBoost = if (prev.isNotEmpty()) bigrams["$prev\t$candidateNorm"] ?: 0 else 0
                val prefixBoost = if (isPrefix) 90.0 else 0.0
                val exactBoost = if (candidateNorm == word) 400.0 else 0.0
                val score = exactBoost + prefixBoost +
                    entry.frequency.coerceAtMost(1_000_000) / 1000.0 +
                    bigramBoost * 8.0 - distance * 140.0
                Suggestion(entry.word, score, distance)
            }
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.distance }.thenBy { it.word.length })
            .distinctBy { normalize(it.word) }
            .take(limit)
            .toList()
    }

    private fun LinkedHashMap<String, WordEntry>.putIfRoom(entry: WordEntry) {
        if (size < MAX_CANDIDATES || containsKey(entry.word)) this[entry.word] = entry
    }

    private fun normalize(s: String): String = s.trim().lowercase(locale)

    private fun generateDeletes1(word: String): Set<String> {
        if (word.length <= 1) return emptySet()
        val out = LinkedHashSet<String>(word.length)
        for (i in word.indices) out.add(word.removeRange(i, i + 1))
        return out
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
