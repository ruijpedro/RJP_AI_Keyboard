package pt.rjp.aikeyboard.ime

import pt.rjp.aikeyboard.core.Language

data class KeySpec(val label: String, val value: String = label, val weight: Float = 1f)

object KeyboardLayout {
    const val SHIFT = "{SHIFT}"
    const val DELETE = "{DEL}"
    const val SPACE = "{SPACE}"
    const val ENTER = "{ENTER}"
    const val SYMBOLS = "{SYM}"
    const val LETTERS = "{ABC}"
    const val LANGUAGE = "{LANG}"

    fun letters(language: Language, upper: Boolean): List<List<KeySpec>> {
        fun row(chars: List<String>) = chars.map { KeySpec(if (upper) it.uppercase() else it) }
        return when (language) {
            Language.PT_PT -> listOf(
                row(listOf("q","w","e","r","t","y","u","i","o","p")),
                row(listOf("a","s","d","f","g","h","j","k","l","ç")),
                listOf(KeySpec("⇧", SHIFT, 1.25f)) + row(listOf("z","x","c","v","b","n","m")) + KeySpec("⌫", DELETE, 1.25f),
                bottom(language)
            )
            Language.EN_GB, Language.IT_IT -> listOf(
                row(listOf("q","w","e","r","t","y","u","i","o","p")),
                row(listOf("a","s","d","f","g","h","j","k","l")),
                listOf(KeySpec("⇧", SHIFT, 1.25f)) + row(listOf("z","x","c","v","b","n","m")) + KeySpec("⌫", DELETE, 1.25f),
                bottom(language)
            )
            Language.ES_ES -> listOf(
                row(listOf("q","w","e","r","t","y","u","i","o","p")),
                row(listOf("a","s","d","f","g","h","j","k","l","ñ")),
                listOf(KeySpec("⇧", SHIFT, 1.25f)) + row(listOf("z","x","c","v","b","n","m")) + KeySpec("⌫", DELETE, 1.25f),
                bottom(language)
            )
            Language.FR_FR -> listOf(
                row(listOf("a","z","e","r","t","y","u","i","o","p")),
                row(listOf("q","s","d","f","g","h","j","k","l","m")),
                listOf(KeySpec("⇧", SHIFT, 1.25f)) + row(listOf("w","x","c","v","b","n")) + KeySpec("⌫", DELETE, 1.25f),
                bottom(language)
            )
            Language.DE_DE -> listOf(
                row(listOf("q","w","e","r","t","z","u","i","o","p","ü")),
                row(listOf("a","s","d","f","g","h","j","k","l","ö","ä")),
                listOf(KeySpec("⇧", SHIFT, 1.25f)) + row(listOf("y","x","c","v","b","n","m")) + KeySpec("⌫", DELETE, 1.25f),
                bottom(language)
            )
        }
    }

    fun symbols(language: Language): List<List<KeySpec>> = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0").map(::KeySpec),
        listOf("@","#","€","_","&","-","+","(",")","/").map(::KeySpec),
        listOf(KeySpec("ABC", LETTERS, 1.35f)) + listOf("*","\"","'",":",";","!","?").map(::KeySpec) + KeySpec("⌫", DELETE, 1.35f),
        bottom(language)
    )

    private fun bottom(language: Language) = listOf(
        KeySpec("123", SYMBOLS, 1.2f),
        KeySpec(language.shortLabel, LANGUAGE, 1.0f),
        KeySpec(",", ",", 0.8f),
        KeySpec("espaço", SPACE, 4.0f),
        KeySpec(".", ".", 0.8f),
        KeySpec("↵", ENTER, 1.2f)
    )
}
