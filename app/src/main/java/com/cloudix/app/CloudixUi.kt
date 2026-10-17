package com.cloudix.app

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val swatches = listOf(
    Color(0xFF7C74D8), Color(0xFF2C8A83), Color(0xFFC56584),
    Color(0xFFBC7748), Color(0xFF497DC5), Color(0xFF76658E),
    Color(0xFF779742), Color(0xFFBD5E5B)
)

@Composable
fun CloudixTheme(settings: CloudixSettings, revision: Int, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> systemDark }
    val ctx = LocalContext.current
    val color = swatches[settings.accent.coerceIn(0, swatches.lastIndex)]
    val dynamic = settings.dynamic && Build.VERSION.SDK_INT >= 31
    val scheme = if (dynamic) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) darkColorScheme(primary = color, secondary = color, tertiary = color)
    else lightColorScheme(primary = color, secondary = color, tertiary = color)
    MaterialTheme(colorScheme = scheme, shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp)), content = content)
}

@Composable
fun CloudixScaffold(
    role: String, page: Int, onPage: (Int) -> Unit,
    header: String, subtitle: String, content: @Composable (PaddingValues) -> Unit
) {
    val pages = if (role == "server") listOf(
        Triple("Accueil", Icons.Rounded.Home, 0),
        Triple("Stockage", Icons.Rounded.Folder, 1),
        Triple("Style", Icons.Rounded.Palette, 3),
        Triple("Options", Icons.Rounded.Settings, 4)
    ) else listOf(
        Triple("Accueil", Icons.Rounded.Home, 0),
        Triple("Fichiers", Icons.Rounded.FolderOpen, 1),
        Triple("Sauvegardes", Icons.Rounded.AutoMode, 2),
        Triple("Style", Icons.Rounded.Palette, 3),
        Triple("Options", Icons.Rounded.Settings, 4)
    )
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(header, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(subtitle, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(if (role == "server") "NAS" else "CLIENT",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                pages.forEach { (label, icon, index) ->
                    NavigationBarItem(selected = page == index, onClick = { onPage(index) },
                        label = { Text(label, maxLines = 1) }, icon = { Icon(icon, null) },
                        alwaysShowLabel = false)
                }
            }
        },
        content = content
    )
}

@Composable
fun WelcomeScreen(onServer: () -> Unit, onClient: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp).verticalScrollCompat(),
            verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(
                        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary
                    ))), contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.CloudSync, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
            Spacer(Modifier.height(24.dp))
            Text("Cloudix", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
            Text("Ton cloud. Tes règles.", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Relie tes appareils, sauvegarde automatiquement tes dossiers et retrouve tes fichiers où tu veux.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(36.dp))
            RoleCard("Ce téléphone est mon serveur", "Il héberge les fichiers et reste disponible sur le Wi-Fi.",
                Icons.Rounded.Dns, onServer)
            Spacer(Modifier.height(14.dp))
            RoleCard("Ce téléphone est mon client", "Il envoie, télécharge et consulte les fichiers à distance.",
                Icons.Rounded.PhoneAndroid, onClient)
            Spacer(Modifier.height(32.dp))
            Text("Privé par défaut  ·  Sans compte  ·  Sans root requis",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoleCard(title: String, description: String, icon: ImageVector, action: () -> Unit) {
    ElevatedCard(onClick = action, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ArrowForwardIos, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun HeroCard(title: String, description: String, icon: ImageVector, actions: @Composable RowScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}
@Composable
fun SectionTitle(title: String, extra: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        if (extra.isNotBlank()) Text(extra, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
    }
}
@Composable
fun SettingItem(icon: ImageVector, title: String, description: String = "",
                end: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(14.dp)) {
            Icon(icon, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        end()
    }
}
@Composable
fun AppearancePage(settings: CloudixSettings, onChange: () -> Unit) {
    val themes = listOf("system" to "Système", "light" to "Clair", "dark" to "Sombre")
    Column(Modifier.fillMaxSize().verticalScrollCompat().padding(20.dp)) {
        SectionTitle("Personnalisation", "Material You")
        Text("Choisis l'ambiance de Cloudix. Les changements s'appliquent instantanément.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Mode d'affichage", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            themes.forEach { (id, title) ->
                FilterChip(selected = settings.theme == id, onClick = {
                    settings.theme = id; onChange()
                }, label = { Text(title) })
            }
        }
        Spacer(Modifier.height(20.dp))
        SettingItem(Icons.Rounded.AutoAwesome, "Couleurs dynamiques",
            "Utiliser les couleurs du fond d'écran") {
            Switch(checked = settings.dynamic, onCheckedChange = {
                settings.dynamic = it; onChange()
            })
        }
        SectionTitle("Accents", "8 palettes")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEachIndexed { index, swatch ->
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(15.dp))
                    .background(swatch).clickable {
                        settings.accent = index; settings.dynamic = false; onChange()
                    }, contentAlignment = Alignment.Center) {
                    if (!settings.dynamic && index == settings.accent) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        ElevatedCard {
            Text("Inspiré par la liberté de personnalisation de Clint Browser, avec une interface originale Cloudix.",
                modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
