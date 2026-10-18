package com.cloudix.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AppearanceChoice(
    val id: String,
    val title: String,
    val description: String,
    val sample: Color,
    val previewDark: Boolean = false
)

private fun isDarkTheme(settings: CloudixSettings): Boolean =
    settings.theme == "dark" || settings.surfaceStyle.startsWith("amoled")

@Composable
fun AppearancePage(settings: CloudixSettings, onChange: () -> Unit) {
    val context = LocalContext.current
    val snapshot = settings.appearance // observed: immediate recompose after every update
    val scheme = MaterialTheme.colorScheme
    var selector by remember { mutableStateOf("") }
    var editingColor by remember { mutableStateOf<CloudixColorRole?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val themeChoices = listOf(
        AppearanceChoice("system", context.getString(R.string.appearance_systeme), context.getString(R.string.appearance_suit_automatiquement_le_theme_de_ton_telephon), Color(0xFF898993)),
        AppearanceChoice("dark", context.getString(R.string.appearance_sombre), context.getString(R.string.appearance_fond_fonce_et_contraste_doux), Color(0xFF22222D), true),
        AppearanceChoice("light", context.getString(R.string.appearance_clair), context.getString(R.string.appearance_surfaces_lumineuses_et_aerees), Color(0xFFE8E6EC))
    )
    val surfaceChoices = listOf(
        AppearanceChoice("soft", context.getString(R.string.style_teinte_legere), context.getString(R.string.appearance_fond_subtil_avec_une_douce_teinte), scheme.primary),
        AppearanceChoice("strong", context.getString(R.string.style_teinte_intense), context.getString(R.string.style_surfaces_plus_profondes_et_colorees), scheme.primary),
        AppearanceChoice("amoled", context.getString(R.string.style_amoled), context.getString(R.string.appearance_noir_veritable_pour_un_contraste_maximal), Color(0xFF060606), true),
        AppearanceChoice("amoled_plain", context.getString(R.string.style_amoled_sans_teinte), context.getString(R.string.appearance_noir_pur_seuls_boutons_et_icones_gardent_leur), Color.Black, true)
    )
    val accents = CloudixAppearanceCatalog.presets.mapIndexed { i, preset ->
        AppearanceChoice(i.toString(), context.getString(preset.labelRes), context.getString(preset.descriptionRes), hexColor(preset.hex))
    }
    val selectedName = context.getString(CloudixAppearanceCatalog.presets[snapshot.accent].labelRes)

    Column(
        Modifier.fillMaxSize().verticalScrollCompat()
            .padding(horizontal = 19.dp, vertical = 8.dp)
    ) {
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, contentDescription = null,
                tint = scheme.primary, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(10.dp))
            Text(context.getString(R.string.appearance_look_feel), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(5.dp))

        Spacer(Modifier.height(19.dp))
        LiveAppearancePreview()
        Spacer(Modifier.height(19.dp))

        SectionTitle(context.getString(R.string.appearance_apparence), context.getString(R.string.appearance_theme))
        AppearanceOptionRow(Icons.Rounded.DarkMode, context.getString(R.string.appearance_theme_de_l_application),
            themeChoices.first { it.id == snapshot.theme }.title,
            onClick = { selector = "theme" })
        AppearanceOptionRow(Icons.Rounded.Layers, context.getString(R.string.appearance_intensite_des_surfaces),
            surfaceChoices.first { it.id == snapshot.surfaceStyle }.title,
            onClick = { selector = "surface" })
        AppearanceOptionRow(Icons.Rounded.ColorLens, context.getString(R.string.role_couleur_d_accent),
            if (snapshot.dynamic) context.getString(R.string.appearance_material_you) else selectedName,
            onClick = { selector = "accent" })

        SettingItem(Icons.Rounded.AutoAwesome, context.getString(R.string.appearance_material_you_dynamique),
            if (android.os.Build.VERSION.SDK_INT >= 31)
                context.getString(R.string.appearance_couleurs_generees_a_partir_du_fond_d_ecran_an)
            else context.getString(R.string.appearance_necessite_android_12_ou_plus_recent)) {
            Switch(checked = snapshot.dynamic && android.os.Build.VERSION.SDK_INT >= 31,
                enabled = android.os.Build.VERSION.SDK_INT >= 31,
                onCheckedChange = {
                    settings.dynamic = it
                    if (it) settings.setColorOverride(CloudixColorRole.ACCENT, null)
                    onChange()
                })
        }

        SectionTitle(context.getString(R.string.appearance_couleurs_individuelles), context.getString(R.string.appearance_hex))

        Spacer(Modifier.height(10.dp))
        Surface(shape = RoundedCornerShape(25.dp),
            color = scheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                CloudixColorRole.entries.forEachIndexed { index, role ->
                    val color = resolvedColor(role, scheme)
                    val current = snapshot.overrides[role.key]
                    Row(Modifier.fillMaxWidth().clickable { editingColor = role }
                        .padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp),
                            color = color,
                            border = BorderStroke(1.dp, scheme.outline.copy(alpha = .45f)),
                            modifier = Modifier.size(42.dp)) {}
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(context.getString(role.titleRes), style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium)
                            Text(current ?: colorCode(color),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant)
                        }
                        if (current != null) {
                            Icon(Icons.Rounded.CheckCircle, null,
                                tint = scheme.primary, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                        }
                        Icon(Icons.Rounded.Edit, contentDescription = context.getString(R.string.label_modifier_1, context.getString(role.titleRes)),
                            modifier = Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                    }
                    if (index != CloudixColorRole.entries.lastIndex) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .42f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { settings.resetColors(); onChange() },
            enabled = snapshot.overrides.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text(context.getString(R.string.appearance_effacer_les_couleurs_personnalisees))
        }
        Spacer(Modifier.height(15.dp))
        SectionTitle(context.getString(R.string.appearance_animations))
        SettingItem(Icons.Rounded.Animation, context.getString(R.string.appearance_transitions_fluides),
            context.getString(R.string.appearance_teintes_animees_selections_souples_et_mouveme)) {
            Switch(checked = snapshot.animations, onCheckedChange = {
                settings.animations = it
                onChange()
            })
        }
        Spacer(Modifier.height(17.dp))
        OutlinedButton(onClick = { showResetConfirm = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.SettingsBackupRestore, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text(context.getString(R.string.appearance_reinitialiser_toute_l_apparence))
        }
        Spacer(Modifier.height(24.dp))
    }

    val choices = when (selector) {
        "theme" -> themeChoices
        "surface" -> surfaceChoices
        "accent" -> accents
        else -> emptyList()
    }
    if (selector.isNotBlank()) {
        AppearanceChoiceDialog(
            title = when (selector) {
                "theme" -> context.getString(R.string.appearance_theme_de_l_application)
                "surface" -> context.getString(R.string.appearance_intensite_des_surfaces)
                else -> context.getString(R.string.role_couleur_d_accent)
            },
            items = choices,
            chosen = when (selector) {
                "theme" -> snapshot.theme
                "surface" -> snapshot.surfaceStyle
                else -> snapshot.accent.toString()
            },
            onChoose = { choice ->
                when (selector) {
                    "theme" -> settings.theme = choice
                    "surface" -> settings.surfaceStyle = choice
                    "accent" -> {
                        settings.dynamic = false
                        settings.setColorOverride(CloudixColorRole.ACCENT, null)
                        settings.accent = choice.toInt()
                    }
                }
                onChange()
                selector = ""
            },
            onDismiss = { selector = "" }
        )
    }

    editingColor?.let { role ->
        ColorEditorDialog(
            role = role,
            initialColor = snapshot.overrides[role.key] ?: colorCode(resolvedColor(role, scheme)),
            isOverride = snapshot.overrides.containsKey(role.key),
            background = scheme.background,
            onSave = { hex ->
                settings.setColorOverride(role, hex)
                if (role == CloudixColorRole.ACCENT) settings.dynamic = false
                editingColor = null
                onChange()
            },
            onReset = {
                settings.setColorOverride(role, null)
                editingColor = null
                onChange()
            },
            onDismiss = { editingColor = null }
        )
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(context.getString(R.string.appearance_reinitialiser_le_theme)) },
            text = { Text(context.getString(R.string.appearance_les_couleurs_les_surfaces_et_les_animations_r)) },
            confirmButton = {
                TextButton(onClick = {
                    settings.resetAppearance()
                    showResetConfirm = false
                    onChange()
                }) { Text(context.getString(R.string.appearance_reinitialiser)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(context.getString(R.string.main_annuler)) }
            }
        )
    }
}

@Composable
private fun LiveAppearancePreview() {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = color.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = color.primaryContainer,
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Palette, contentDescription = null,
                        modifier = Modifier.padding(12.dp), tint = color.onPrimaryContainer)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(context.getString(R.string.appearance_apercu_en_direct), fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge)

                }
                Surface(shape = RoundedCornerShape(14.dp),
                    color = color.primaryContainer) {
                    Icon(Icons.Rounded.Check, contentDescription = null,
                        modifier = Modifier.padding(8.dp), tint = color.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(17.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}, enabled = false,
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = color.primary,
                        disabledContentColor = color.onPrimary)) {
                    Text(context.getString(R.string.appearance_bouton))
                }
                Surface(color = color.surface, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = color.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(context.getString(R.string.appearance_carte), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(color.primary, color.primaryContainer, color.surface,
                    color.background, color.onSurface).forEach { tint ->
                    Surface(color = tint, shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, color.outline.copy(alpha=.32f)),
                        modifier = Modifier.weight(1f).height(18.dp)) {}
                }
            }
        }
    }
}

@Composable
private fun AppearanceOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, value: String, onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick,
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(23.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = colors.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp)) {
                Icon(icon, null, tint = colors.primary,
                    modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.primary)
        }
    }
}

@Composable
private fun AppearanceChoiceDialog(
    title: String,
    items: List<AppearanceChoice>,
    chosen: String,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    // Long accent lists open on the current selection, not at the first preset.
    val selectedIndex = items.indexOfFirst { it.id == chosen }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 490.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { option ->
                    val selected = chosen == option.id
                    val border = if (selected)
                        BorderStroke(2.5.dp, colors.primary)
                    else BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .45f))
                    Surface(
                        onClick = { onChoose(option.id) },
                        color = if (selected) colors.primaryContainer
                            else colors.surfaceContainerHigh,
                        border = border,
                        shape = RoundedCornerShape(21.dp),
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiniThemePreview(option.sample, option.previewDark)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(option.title, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(option.description, style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant)
                            }
                            if (selected) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Rounded.Check, context.getString(R.string.appearance_selectionne), tint = colors.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.appearance_fermer)) }
        },
        shape = RoundedCornerShape(31.dp)
    )
}

@Composable
private fun MiniThemePreview(accent: Color, isDark: Boolean) {
    val background = if (isDark) Color(0xFF090909)
        else androidx.compose.ui.graphics.lerp(Color(0xFFEAE6DD), accent, .10f)
    Surface(color = background, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.size(52.dp)) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(color = accent, shape = RoundedCornerShape(5.dp),
                modifier = Modifier.fillMaxWidth(.80f).height(15.dp)) {}
            Surface(color = if (isDark) Color(0xFF33333A) else Color(0xFFD6D2CD),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(10.dp)) {}
        }
    }
}

private fun resolvedColor(role: CloudixColorRole, colors: ColorScheme): Color =
    when (role) {
        CloudixColorRole.BACKGROUND -> colors.background
        CloudixColorRole.SURFACE -> colors.surfaceContainerHigh
        CloudixColorRole.TEXT -> colors.onSurface
        CloudixColorRole.ACCENT -> colors.primary
        CloudixColorRole.SELECTION -> colors.primaryContainer
        CloudixColorRole.OUTLINE -> colors.outline
        CloudixColorRole.SCROLLBAR -> colors.primary
    }

private fun colorCode(value: Color): String =
    "#%06X".format(value.toArgb() and 0xFFFFFF)

private fun contrastRatio(a: Color, b: Color): Float {
    val x = a.luminance()
    val y = b.luminance()
    return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
}

@Composable
private fun ColorEditorDialog(
    role: CloudixColorRole,
    initialColor: String,
    isOverride: Boolean,
    background: Color,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var input by remember(role) { mutableStateOf(initialColor) }
    val parsed = CloudixHex.normalize(input)
    val preview = parsed?.let { hexColor(it) } ?: MaterialTheme.colorScheme.primary
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(role.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(context.getString(role.descriptionRes), color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = preview,
                        border = BorderStroke(1.dp, colors.outline),
                        modifier = Modifier.size(68.dp)) {}
                    Spacer(Modifier.width(13.dp))
                    OutlinedTextField(value = input, onValueChange = {
                        input = it.take(9)
                    }, label = { Text(context.getString(R.string.appearance_hexadecimal)) },
                        placeholder = { Text("#D8B65A") },
                        singleLine = true, isError = parsed == null,
                        modifier = Modifier.weight(1f))
                }
                Text(if (parsed == null)
                    context.getString(R.string.appearance_saisis_une_couleur_au_format_rrggbb)
                else context.getString(R.string.appearance_appuie_sur_appliquer_toute_l_interface_change),
                    color = if (parsed == null) colors.error else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#D8B65A", "#6750A4", "#279F87", "#C05268", "#477CD1", "#F5F5F5")
                        .forEach { hex ->
                            Surface(onClick = { input = hex }, color = hexColor(hex),
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, colors.outline.copy(alpha = .5f)),
                                modifier = Modifier.weight(1f).height(35.dp)) {}
                        }
                }
                if (role == CloudixColorRole.TEXT && parsed != null &&
                    contrastRatio(preview, background) < 4.5f) {
                    Text(context.getString(R.string.appearance_contraste_inferieur_a_4_5_1_ce_texte_risque_d),
                        color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onSave) }) {
                Text(context.getString(R.string.appearance_appliquer))
            }
        },
        dismissButton = {
            Row {
                if (isOverride) TextButton(onClick = onReset) { Text(context.getString(R.string.appearance_reinitialiser)) }
                TextButton(onClick = onDismiss) { Text(context.getString(R.string.main_annuler)) }
            }
        },
        shape = RoundedCornerShape(29.dp)
    )
}
