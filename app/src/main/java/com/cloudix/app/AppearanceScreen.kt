package com.cloudix.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val snapshot = settings.appearance // observed: immediate recompose after every update
    val scheme = MaterialTheme.colorScheme
    var selector by remember { mutableStateOf("") }
    var editingColor by remember { mutableStateOf<CloudixColorRole?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val themeChoices = listOf(
        AppearanceChoice("system", "Système", "Suit automatiquement le thème de ton téléphone", Color(0xFF898993)),
        AppearanceChoice("dark", "Sombre", "Fond foncé et contraste doux", Color(0xFF22222D), true),
        AppearanceChoice("light", "Clair", "Surfaces lumineuses et aérées", Color(0xFFE8E6EC))
    )
    val surfaceChoices = listOf(
        AppearanceChoice("soft", "Teinte légère", "Fond subtil avec une douce teinte", scheme.primary),
        AppearanceChoice("strong", "Teinte intense", "Surfaces plus profondes et colorées", scheme.primary),
        AppearanceChoice("amoled", "AMOLED", "Noir véritable pour un contraste maximal", Color(0xFF060606), true),
        AppearanceChoice("amoled_plain", "AMOLED sans teinte", "Noir pur, seuls boutons et icônes gardent leur accent", Color.Black, true)
    )
    val accents = CloudixAppearanceCatalog.presets.mapIndexed { i, preset ->
        AppearanceChoice(i.toString(), preset.label, preset.description, hexColor(preset.hex))
    }
    val selectedName = CloudixAppearanceCatalog.presets[snapshot.accent].label

    Column(
        Modifier.fillMaxSize().verticalScrollCompat()
            .padding(horizontal = 19.dp, vertical = 8.dp)
    ) {
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, contentDescription = null,
                tint = scheme.primary, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(10.dp))
            Text("Look & Feel", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(5.dp))
        Text("Ton style, sans redémarrage. Chaque choix est visible immédiatement.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(19.dp))
        LiveAppearancePreview()
        Spacer(Modifier.height(19.dp))

        SectionTitle("Apparence", "THÈME")
        AppearanceOptionRow(Icons.Rounded.DarkMode, "Thème de l'application",
            themeChoices.first { it.id == snapshot.theme }.title,
            onClick = { selector = "theme" })
        AppearanceOptionRow(Icons.Rounded.Layers, "Intensité des surfaces",
            surfaceChoices.first { it.id == snapshot.surfaceStyle }.title,
            onClick = { selector = "surface" })
        AppearanceOptionRow(Icons.Rounded.ColorLens, "Couleur d'accent",
            if (snapshot.dynamic) "Material You" else selectedName,
            onClick = { selector = "accent" })

        SettingItem(Icons.Rounded.AutoAwesome, "Material You dynamique",
            if (android.os.Build.VERSION.SDK_INT >= 31)
                "Couleurs générées à partir du fond d'écran Android"
            else "Nécessite Android 12 ou plus récent") {
            Switch(checked = snapshot.dynamic && android.os.Build.VERSION.SDK_INT >= 31,
                enabled = android.os.Build.VERSION.SDK_INT >= 31,
                onCheckedChange = {
                    settings.dynamic = it
                    if (it) settings.setColorOverride(CloudixColorRole.ACCENT, null)
                    onChange()
                })
        }

        SectionTitle("Couleurs individuelles", "HEX")
        Text("Modifie précisément chaque élément. Une couleur personnalisée remplace la valeur du thème pour cet élément.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
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
                            Text(role.title, style = MaterialTheme.typography.bodyLarge,
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
                        Icon(Icons.Rounded.Edit, contentDescription = "Modifier ${role.title}",
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
            Text("Effacer les couleurs personnalisées")
        }
        Spacer(Modifier.height(15.dp))
        SectionTitle("Animations")
        SettingItem(Icons.Rounded.Animation, "Transitions fluides",
            "Teintes animées, sélections souples et mouvement de navigation") {
            Switch(checked = snapshot.animations, onCheckedChange = {
                settings.animations = it
                onChange()
            })
        }
        Spacer(Modifier.height(17.dp))
        OutlinedButton(onClick = { showResetConfirm = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.SettingsBackupRestore, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text("Réinitialiser toute l'apparence")
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
                "theme" -> "Thème de l'application"
                "surface" -> "Intensité des surfaces"
                else -> "Couleur d'accent"
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
            title = { Text("Réinitialiser le thème ?") },
            text = { Text("Les couleurs, les surfaces et les animations reviennent aux paramètres Cloudix par défaut.") },
            confirmButton = {
                TextButton(onClick = {
                    settings.resetAppearance()
                    showResetConfirm = false
                    onChange()
                }) { Text("Réinitialiser") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun LiveAppearancePreview() {
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
                    Text("Aperçu en direct", fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge)
                    Text("Thème appliqué dans toute l'application",
                        style = MaterialTheme.typography.bodySmall,
                        color = color.onSurfaceVariant)
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
                    Text("Bouton")
                }
                Surface(color = color.surface, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = color.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Carte", style = MaterialTheme.typography.labelLarge)
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
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 490.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                Icon(Icons.Rounded.Check, "Sélectionné", tint = colors.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
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
    var input by remember(role) { mutableStateOf(initialColor) }
    val parsed = CloudixHex.normalize(input)
    val preview = parsed?.let { hexColor(it) } ?: MaterialTheme.colorScheme.primary
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(role.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(role.description, color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = preview,
                        border = BorderStroke(1.dp, colors.outline),
                        modifier = Modifier.size(68.dp)) {}
                    Spacer(Modifier.width(13.dp))
                    OutlinedTextField(value = input, onValueChange = {
                        input = it.take(9)
                    }, label = { Text("Hexadécimal") },
                        placeholder = { Text("#D8B65A") },
                        singleLine = true, isError = parsed == null,
                        modifier = Modifier.weight(1f))
                }
                Text(if (parsed == null)
                    "Saisis une couleur au format #RRGGBB."
                else "Appuie sur Appliquer : toute l'interface change immédiatement.",
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
                    kotlin.math.abs(preview.toArgb() - background.toArgb()) < 512) {
                    Text("Attention : assure-toi que le texte reste lisible sur le fond choisi.",
                        color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onSave) }) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            Row {
                if (isOverride) TextButton(onClick = onReset) { Text("Réinitialiser") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
        shape = RoundedCornerShape(29.dp)
    )
}
