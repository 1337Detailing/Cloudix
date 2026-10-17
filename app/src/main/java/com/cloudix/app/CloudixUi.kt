package com.cloudix.app

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val swatches = listOf(
    0xFF6750A4, 0xFF327E7C, 0xFFB65380, 0xFFAD6A36, 0xFF346CB5, 0xFF8263A9,
    0xFF5A803F, 0xFFB2524F, 0xFF6750CA, 0xFF0A8B9D, 0xFFB44177, 0xFF935C21,
    0xFF1F7C82, 0xFF7448A0, 0xFF3D9062, 0xFFAD4845, 0xFF5468B7, 0xFF168E8B,
    0xFFAD5989, 0xFF8C7240, 0xFF4B7F99, 0xFF8859B5, 0xFF759144, 0xFFA45B47,
    0xFF8353C8, 0xFF427DCA, 0xFFB15697, 0xFFBA6440, 0xFF3D907F, 0xFF84709F
).map(::Color)
private val white = Color(0xFFFDFCFF)
private val darkSurface = Color(0xFF11121A)
private fun seededScheme(seed: Color, dark: Boolean, intensity: Int): ColorScheme {
    val primary = if (dark) lerp(seed, Color.White, .32f) else seed
    val container = if (dark) lerp(seed, darkSurface, .67f) else lerp(seed, white, .86f)
    val secondary = if (dark) lerp(seed, Color.White, .48f) else lerp(seed, Color.Black, .17f)
    val surface = if (dark) darkSurface else Color(0xFFFCFBFF)
    val blend = (intensity.coerceIn(0, 4) / 4f)
    val elevated = if (dark) lerp(Color(0xFF20212B), lerp(seed, darkSurface, .78f), blend)
        else lerp(Color(0xFFF2F1F7), lerp(seed, white, .85f), blend)
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = Color(0xFF15131D),
        primaryContainer = container, onPrimaryContainer = Color(0xFFF6F1FF),
        secondary = secondary, onSecondary = Color(0xFF12131A),
        secondaryContainer = lerp(seed, darkSurface, .77f),
        onSecondaryContainer = Color(0xFFF3EDFA),
        tertiary = lerp(seed, Color.White, .6f),
        background = Color(0xFF0C0D13), onBackground = Color(0xFFF3F0F7),
        surface = surface, onSurface = Color(0xFFF5F1F7),
        surfaceContainer = elevated, surfaceContainerLow = Color(0xFF15151F),
        surfaceContainerHigh = lerp(seed, darkSurface, .81f),
        surfaceVariant = Color(0xFF282A36),
        onSurfaceVariant = Color(0xFFC1BFCC),
        outline = Color(0xFF7F7B89)
    ) else lightColorScheme(
        primary = primary, onPrimary = Color.White,
        primaryContainer = container, onPrimaryContainer = lerp(seed, Color.Black, .54f),
        secondary = secondary, onSecondary = Color.White,
        secondaryContainer = lerp(seed, white, .89f),
        onSecondaryContainer = lerp(seed, Color.Black, .5f),
        tertiary = lerp(seed, Color.Black, .12f),
        background = Color(0xFFFAF9FD), onBackground = Color(0xFF1B1922),
        surface = surface, onSurface = Color(0xFF1B1922),
        surfaceContainer = elevated, surfaceContainerLow = Color(0xFFFCFBFF),
        surfaceContainerHigh = lerp(seed, white, .91f),
        surfaceVariant = Color(0xFFF0EDF5),
        onSurfaceVariant = Color(0xFF55515E),
        outline = Color(0xFF7D7885)
    )
}

@Composable
fun CloudixTheme(settings: CloudixSettings, revision: Int, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (settings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // Android 12+ dynamic colors originate from the OS wallpaper palette.
    // OEMs that omit Monet resources can always use a complete Cloudix palette instead.
    val systemPalette = settings.dynamic && Build.VERSION.SDK_INT >= 31
    val fallback = seededScheme(swatches[settings.accent.coerceIn(0, swatches.lastIndex)], dark, settings.surfaceIntensity)
    val colors = if (systemPalette) {
        runCatching {
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }.getOrElse { fallback }
    } else fallback
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(9.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(34.dp)
        ), content = content
    )
}

@Composable
fun CloudixScaffold(
    role: String, page: Int, onPage: (Int) -> Unit,
    header: String, subtitle: String, content: @Composable (PaddingValues) -> Unit
) {
    val tabs = if (role == "server") listOf(
        Triple("Accueil", Icons.Rounded.Home, 0),
        Triple("Stockage", Icons.Rounded.Folder, 1),
        Triple("Thèmes", Icons.Rounded.Palette, 3),
        Triple("Réglages", Icons.Rounded.Tune, 4)
    ) else listOf(
        Triple("Accueil", Icons.Rounded.Home, 0),
        Triple("Fichiers", Icons.Rounded.FolderOpen, 1),
        Triple("Backup", Icons.Rounded.Backup, 2),
        Triple("Thèmes", Icons.Rounded.Palette, 3),
        Triple("Réglages", Icons.Rounded.Tune, 4)
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(start = 22.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary))),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(27.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(header, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold)
                        Text(subtitle, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp)) {
                        Text(if (role == "server") "SERVEUR" else "CLIENT",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface) {
                NavigationBar(modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp) {
                    tabs.forEach { (title, icon, index) ->
                        NavigationBarItem(
                            selected = page == index,
                            onClick = { onPage(index) },
                            label = { Text(title, maxLines = 1) },
                            icon = { Icon(icon, contentDescription = title) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                }
            }
        }, content = content
    )
}

@Composable
fun WelcomeScreen(onServer: () -> Unit, onClient: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().verticalScrollCompat()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(50.dp)) }
        Spacer(Modifier.height(30.dp))
        Text("Bienvenue sur", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Cloudix.", style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text("Ton espace, sur tous tes appareils.",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("Aucun abonnement. Aucun cloud imposé. Choisis le rôle de ce téléphone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(34.dp))
        RoleCard("Mon serveur", "Je garde les fichiers et partage mon stockage en Wi-Fi",
            Icons.Rounded.Dns, onServer)
        Spacer(Modifier.height(14.dp))
        RoleCard("Mon client", "J'explore, j'envoie et je sauvegarde mes dossiers",
            Icons.Rounded.PhoneAndroid, onClient)
        Spacer(Modifier.height(28.dp))
        Surface(shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text("Connexion chiffrée · Stockage privé · Sans root obligatoire",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun RoleCard(title: String, description: String, icon: ImageVector, action: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "cloudix-card-spring")
    ElevatedCard(onClick = action, shape = RoundedCornerShape(28.dp),
        interactionSource = interactionSource,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().scale(scale)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(17.dp).size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(5.dp))
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ArrowForwardIos, null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun HeroCard(title: String, description: String, icon: ImageVector,
    actions: @Composable RowScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer)))) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Surface(color = primary.copy(alpha = .13f), shape = RoundedCornerShape(18.dp)) {
                    Icon(icon, null, modifier = Modifier.padding(14.dp).size(30.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(22.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(10.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .85f))
                Spacer(Modifier.height(21.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), content = actions)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, extra: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (extra.isNotBlank()) Text(extra, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SettingItem(icon: ImageVector, title: String, description: String = "",
    end: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().animateContentSize().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(18.dp)) {
            Icon(icon, null, modifier = Modifier.padding(13.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge)
            if (description.isNotEmpty()) Text(description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        end()
    }
}

@Composable
fun AppearancePage(settings: CloudixSettings, onChange: () -> Unit) {
    val themes = listOf("system" to "Auto", "light" to "Clair", "dark" to "Sombre")
    Column(Modifier.fillMaxSize().verticalScrollCompat()
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle("Ton Cloudix", "PERSONNALISATION")
        Text("Style inspiré des animations fluides d'OriginOS 7 et des options de Clint Browser.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Aperçu en direct", fontWeight = FontWeight.ExtraBold)
                        Text("La palette s'applique à toute l'application",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary) {
                        Text("Bouton", Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Surface(shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surface) {
                        Text("Cartes", Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Affichage")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            themes.forEach { (id, label) ->
                FilterChip(selected = settings.theme == id,
                    onClick = { settings.theme = id; onChange() },
                    label = { Text(label) },
                    leadingIcon = if (settings.theme == id) ({
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                    }) else null)
            }
        }
        Spacer(Modifier.height(18.dp))
        SettingItem(Icons.Rounded.AutoAwesome, "Material You",
            if (Build.VERSION.SDK_INT >= 31)
                "Synchroniser la palette avec les couleurs système Android"
            else "Disponible à partir d’Android 12") {
            Switch(checked = settings.dynamic && Build.VERSION.SDK_INT >= 31,
                enabled = Build.VERSION.SDK_INT >= 31,
                onCheckedChange = { settings.dynamic = it; onChange() })
        }
        SectionTitle("Palettes Cloudix", "30 STYLES")
        Text("Les palettes personnalisées remplacent les couleurs dynamiques et recolorent toutes les surfaces.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        swatches.chunked(6).forEachIndexed { rowIndex, group ->
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                group.forEachIndexed { colIndex, color ->
                    val index = rowIndex * 6 + colIndex
                    Surface(onClick = {
                        settings.dynamic = false
                        settings.accent = index
                        onChange()
                    }, shape = RoundedCornerShape(16.dp),
                        color = color, modifier = Modifier.weight(1f).height(48.dp),
                        border = if (settings.accent == index && !settings.dynamic)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground)
                        else null) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!settings.dynamic && settings.accent == index)
                                Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
        }
        SectionTitle("Intensité des surfaces", "${settings.surfaceIntensity + 1} / 5")
        Slider(value = settings.surfaceIntensity.toFloat(),
            onValueChange = { settings.surfaceIntensity = it.toInt(); onChange() },
            valueRange = 0f..4f, steps = 3,
            enabled = !settings.dynamic)
        Text("Adapte l'intensité des cartes et des fonds teintés. Désactive Material You pour utiliser ce réglage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        SectionTitle("Mouvement")
        SettingItem(Icons.Rounded.Animation, "Animations fluides",
            "Transitions, rebonds subtils et navigation animée") {
            Switch(checked = settings.animations, onCheckedChange = {
                settings.animations = it; onChange()
            })
        }
        Spacer(Modifier.height(20.dp))
        Text("Sur certains smartphones, le constructeur ne publie pas ses couleurs système. Dans ce cas, choisis une palette Cloudix pour un résultat garanti.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))
