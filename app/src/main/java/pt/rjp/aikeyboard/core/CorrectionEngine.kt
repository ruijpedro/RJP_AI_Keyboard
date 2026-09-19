package pt.rjp.aikeyboard.core

class CorrectionEngine(private val repository: DictionaryRepository) {
    fun suggestions(language: Language, word: String, previousWord: String?, limit: Int = 3): List<Suggestion> {
        if (word.isBlank()) return emptyList()
        return repository.get(language).suggestions(word, previousWord, repository.bigrams(language), limit)
    }

    fun autoCorrection(language: Language, word: String, previousWord: String?): String? {
        if (word.length < 3) return null
        val lexicon = repository.get(language)
        if (lexicon.contains(word)) return null
        val best = lexicon.suggestions(word, previousWord, repository.bigrams(language), 1).firstOrNull() ?: return null
        val allowedDistance = when {
            word.length <= 4 -> 1
            word.length <= 8 -> 1
            else -> 2
        }
        return if (best.distance <= allowedDistance && best.score > -40) preserveCase(word, best.word) else null
    }

    fun learn(language: Language, word: String) = repository.learn(language, word)

    private fun preserveCase(source: String, replacement: String): String {
        return if (source.firstOrNull()?.isUpperCase() == true) replacement.replaceFirstChar { it.uppercase() } else replacement
    }
}
