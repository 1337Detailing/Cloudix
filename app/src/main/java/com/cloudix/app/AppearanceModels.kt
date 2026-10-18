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

enum class CloudixColorRole(val key: String, val titleRes: Int, val descriptionRes: Int) {
    BACKGROUND("background", R.string.role_arriere_plan, R.string.role_fond_principal_de_l_application),
    SURFACE("surface", R.string.role_cartes_et_surfaces, R.string.role_cartes_menus_et_panneaux),
    TEXT("text", R.string.role_texte_principal, R.string.role_titres_et_textes_courants),
    ACCENT("accent", R.string.role_couleur_d_accent, R.string.role_boutons_icones_et_elements_actifs),
    SELECTION("selection", R.string.role_selection, R.string.role_indicateurs_surlignages_et_onglets),
    OUTLINE("outline", R.string.role_contours, R.string.role_bordures_et_separateurs),
    SCROLLBAR("scrollbar", R.string.role_barre_de_defilement, R.string.role_curseur_des_pages_defilantes);

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

data class CloudixAccentPreset(
    val labelRes: Int,
    val descriptionRes: Int,
    val hex: String
)

object CloudixAppearanceCatalog {
    val presets = listOf(
        CloudixAccentPreset(R.string.preset_lavande, R.string.preset_violet_doux_et_moderne, "#6750A4"),
        CloudixAccentPreset(R.string.preset_sarcelle, R.string.preset_vert_d_eau_equilibre, "#327E7C"),
        CloudixAccentPreset(R.string.preset_rose, R.string.preset_rose_elegant_et_subtil, "#B65380"),
        CloudixAccentPreset(R.string.preset_cuivre, R.string.preset_tons_chauds_et_cuivres, "#AD6A36"),
        CloudixAccentPreset(R.string.preset_bleu, R.string.preset_bleu_profond, "#346CB5"),
        CloudixAccentPreset(R.string.preset_orchidee, R.string.preset_violet_raffine, "#8263A9"),
        CloudixAccentPreset(R.string.preset_olive, R.string.preset_vert_naturel, "#5A803F"),
        CloudixAccentPreset(R.string.preset_terracotta, R.string.preset_rouge_brique_chaleureux, "#B2524F"),
        CloudixAccentPreset(R.string.preset_iris, R.string.preset_violet_eclatant, "#6750CA"),
        CloudixAccentPreset(R.string.preset_cyan, R.string.preset_bleu_turquoise_energique, "#0A8B9D"),
        CloudixAccentPreset(R.string.preset_framboise, R.string.preset_rose_fruite, "#B44177"),
        CloudixAccentPreset(R.string.preset_moutarde, R.string.preset_dore_terreux, "#935C21"),
        CloudixAccentPreset(R.string.preset_ocean, R.string.preset_bleu_ocean, "#1F7C82"),
        CloudixAccentPreset(R.string.preset_amethyste, R.string.preset_violet_profond, "#7448A0"),
        CloudixAccentPreset(R.string.preset_jade, R.string.preset_vert_frais, "#3D9062"),
        CloudixAccentPreset(R.string.preset_rouge, R.string.preset_rouge_elegant, "#AD4845"),
        CloudixAccentPreset(R.string.preset_bleu_acier, R.string.preset_bleu_froid_et_discret, "#5468B7"),
        CloudixAccentPreset(R.string.preset_menthe, R.string.preset_vert_menthe_lumineux, "#168E8B"),
        CloudixAccentPreset(R.string.preset_pivoine, R.string.preset_rose_legerement_violace, "#AD5989"),
        CloudixAccentPreset(R.string.preset_sable, R.string.preset_brun_dore_doux, "#8C7240"),
        CloudixAccentPreset(R.string.preset_ardoise, R.string.preset_bleu_gris_neutre, "#4B7F99"),
        CloudixAccentPreset(R.string.preset_violet, R.string.preset_violet_intense, "#8859B5"),
        CloudixAccentPreset(R.string.preset_pistache, R.string.preset_vert_doux, "#759144"),
        CloudixAccentPreset(R.string.preset_corail, R.string.preset_rouge_orange_lumineux, "#A45B47"),
        CloudixAccentPreset(R.string.preset_electrique, R.string.preset_violet_electrique, "#8353C8"),
        CloudixAccentPreset(R.string.preset_azur, R.string.preset_bleu_azur, "#427DCA"),
        CloudixAccentPreset(R.string.preset_fuchsia, R.string.preset_rose_vibrant, "#B15697"),
        CloudixAccentPreset(R.string.preset_orange, R.string.preset_orange_terre_cuite, "#BA6440"),
        CloudixAccentPreset(R.string.preset_emeraude, R.string.preset_vert_emeraude, "#3D907F"),
        CloudixAccentPreset(R.string.preset_prune, R.string.preset_prune_apaisante, "#84709F"),
        CloudixAccentPreset(R.string.preset_or, R.string.preset_jaune_dore_intense, "#D8B65A"),
        CloudixAccentPreset(R.string.preset_crimson, R.string.preset_rouge_profond, "#972D3A"),
        CloudixAccentPreset(R.string.preset_ruby, R.string.preset_rouge_rubis, "#A12347"),
        CloudixAccentPreset(R.string.preset_rose_poudre, R.string.preset_rose_tres_doux, "#C576A0"),
        CloudixAccentPreset(R.string.preset_graphite, R.string.preset_gris_legerement_bleute, "#69778B"),
        CloudixAccentPreset(R.string.preset_citron, R.string.preset_jaune_lumineux, "#A7A32C")
    )
    val styles = listOf(
        Triple("soft", R.string.style_teinte_legere, R.string.style_arriere_plan_subtilement_colore),
        Triple("strong", R.string.style_teinte_intense, R.string.style_surfaces_plus_profondes_et_colorees),
        Triple("amoled", R.string.style_amoled, R.string.style_noir_veritable_surfaces_legerement_teintees),
        Triple("amoled_plain", R.string.style_amoled_sans_teinte, R.string.style_noir_veritable_accent_seulement)
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
