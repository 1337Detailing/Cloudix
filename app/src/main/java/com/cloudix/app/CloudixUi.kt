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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
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
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.Black), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "Logo Cloudix",
                            modifier = Modifier.fillMaxSize())
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
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp))
            .background(Color.Black), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Logo Cloudix",
                modifier = Modifier.fillMaxSize())
        }
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
fun Modifier.verticalScrollCompat(): Modifier {
    val state = rememberScrollState()
    val thumb = LocalCloudixScrollbar.current
    return this.then(Modifier.drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max > 0 && max < Int.MAX_VALUE) {
            val unit = 1.dp.toPx()
            val thumbHeight = (size.height * size.height / (size.height + max))
                .coerceIn(28.dp.toPx(), size.height)
            val fraction = (state.value.toFloat() / max).coerceIn(0f, 1f)
            drawRoundRect(
                color = thumb.copy(alpha = .72f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - 6 * unit,
                    (size.height - thumbHeight) * fraction),
                size = androidx.compose.ui.geometry.Size(3 * unit, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3 * unit)
            )
        }
    }).verticalScroll(state)
}
