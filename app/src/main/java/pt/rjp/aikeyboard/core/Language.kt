package pt.rjp.aikeyboard.core

enum class Language(
    val tag: String,
    val shortLabel: String,
    val displayName: String,
    val dictionaryAsset: String,
    val bigramAsset: String
) {
    PT_PT("pt-PT", "PT", "Português (Portugal)", "pt_PT.tsv", "pt_PT.tsv"),
    EN_GB("en-GB", "EN", "English", "en_GB.tsv", "en_GB.tsv"),
    IT_IT("it-IT", "IT", "Italiano", "it_IT.tsv", "it_IT.tsv"),
    ES_ES("es-ES", "ES", "Español", "es_ES.tsv", "es_ES.tsv"),
    FR_FR("fr-FR", "FR", "Français", "fr_FR.tsv", "fr_FR.tsv"),
    DE_DE("de-DE", "DE", "Deutsch", "de_DE.tsv", "de_DE.tsv");

    companion object {
        fun fromTag(tag: String?): Language = entries.firstOrNull { it.tag == tag } ?: PT_PT
    }
}
