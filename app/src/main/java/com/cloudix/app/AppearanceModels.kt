package com.cloudix.app

import androidx.compose.runtime.Immutable

/**
 * A single immutable appearance state keeps all theme consumers in sync.
 * User changes replace this value, causing Compose to recompose immediately.
 */
@Immutable
data class AppearanceSnapshot(
    val theme: String = "system",
    val dynamic: Boolean = true,
    val accent: Int = 0,
    val surfaceStyle: String = "soft",
    val animations: Boolean = true,
    val overrides: Map<String, String> = emptyMap()
)

enum class CloudixColorRole(val key: String, val title: String, val description: String) {
    BACKGROUND("background", "Arrière-plan", "Fond principal de l'application"),
    SURFACE("surface", "Cartes et surfaces", "Cartes, menus et panneaux"),
    TEXT("text", "Texte principal", "Titres et textes courants"),
    ACCENT("accent", "Couleur d'accent", "Boutons, icônes et éléments actifs"),
    SELECTION("selection", "Sélection", "Indicateurs, surlignages et onglets"),
    OUTLINE("outline", "Contours", "Bordures et séparateurs"),
    SCROLLBAR("scrollbar", "Barre de défilement", "Curseur des pages défilantes");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

data class CloudixAccentPreset(
    val label: String,
    val description: String,
    val hex: String
)

object CloudixAppearanceCatalog {
    val presets = listOf(
        CloudixAccentPreset("Lavande", "Violet doux et moderne", "#6750A4"),
        CloudixAccentPreset("Sarcelle", "Vert d'eau équilibré", "#327E7C"),
        CloudixAccentPreset("Rose", "Rose élégant et subtil", "#B65380"),
        CloudixAccentPreset("Cuivre", "Tons chauds et cuivrés", "#AD6A36"),
        CloudixAccentPreset("Bleu", "Bleu profond", "#346CB5"),
        CloudixAccentPreset("Orchidée", "Violet raffiné", "#8263A9"),
        CloudixAccentPreset("Olive", "Vert naturel", "#5A803F"),
        CloudixAccentPreset("Terracotta", "Rouge brique chaleureux", "#B2524F"),
        CloudixAccentPreset("Iris", "Violet éclatant", "#6750CA"),
        CloudixAccentPreset("Cyan", "Bleu turquoise énergique", "#0A8B9D"),
        CloudixAccentPreset("Framboise", "Rose fruité", "#B44177"),
        CloudixAccentPreset("Moutarde", "Doré terreux", "#935C21"),
        CloudixAccentPreset("Océan", "Bleu océan", "#1F7C82"),
        CloudixAccentPreset("Améthyste", "Violet profond", "#7448A0"),
        CloudixAccentPreset("Jade", "Vert frais", "#3D9062"),
        CloudixAccentPreset("Rouge", "Rouge élégant", "#AD4845"),
        CloudixAccentPreset("Bleu acier", "Bleu froid et discret", "#5468B7"),
        CloudixAccentPreset("Menthe", "Vert menthe lumineux", "#168E8B"),
        CloudixAccentPreset("Pivoine", "Rose légèrement violacé", "#AD5989"),
        CloudixAccentPreset("Sable", "Brun doré doux", "#8C7240"),
        CloudixAccentPreset("Ardoise", "Bleu gris neutre", "#4B7F99"),
        CloudixAccentPreset("Violet", "Violet intense", "#8859B5"),
        CloudixAccentPreset("Pistache", "Vert doux", "#759144"),
        CloudixAccentPreset("Corail", "Rouge orangé lumineux", "#A45B47"),
        CloudixAccentPreset("Électrique", "Violet électrique", "#8353C8"),
        CloudixAccentPreset("Azur", "Bleu azur", "#427DCA"),
        CloudixAccentPreset("Fuchsia", "Rose vibrant", "#B15697"),
        CloudixAccentPreset("Orange", "Orange terre cuite", "#BA6440"),
        CloudixAccentPreset("Émeraude", "Vert émeraude", "#3D907F"),
        CloudixAccentPreset("Prune", "Prune apaisante", "#84709F"),
        CloudixAccentPreset("Or", "Jaune doré intense", "#D8B65A"),
        CloudixAccentPreset("Crimson", "Rouge profond", "#972D3A"),
        CloudixAccentPreset("Ruby", "Rouge rubis", "#A12347"),
        CloudixAccentPreset("Rose poudré", "Rosé très doux", "#C576A0"),
        CloudixAccentPreset("Graphite", "Gris légèrement bleuté", "#69778B"),
        CloudixAccentPreset("Citron", "Jaune lumineux", "#A7A32C")
    )
    val styles = listOf(
        Triple("soft", "Teinte légère", "Arrière-plan subtilement coloré"),
        Triple("strong", "Teinte intense", "Surfaces plus profondes et colorées"),
        Triple("amoled", "AMOLED", "Noir véritable, surfaces légèrement teintées"),
        Triple("amoled_plain", "AMOLED sans teinte", "Noir véritable, accent seulement")
    )
}

/** Strict #RRGGBB editor format; no alpha/transparency that could break legibility. */
object CloudixHex {
    private val pattern = Regex("^#[0-9A-Fa-f]{6}$")
    fun normalize(raw: String): String? {
        val formatted = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
        return formatted.takeIf { pattern.matches(it) }?.uppercase()
    }
}
