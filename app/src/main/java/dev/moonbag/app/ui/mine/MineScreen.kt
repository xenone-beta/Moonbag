@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.mine

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.moonbag.app.R
import dev.moonbag.app.game.Clicker
import dev.moonbag.app.game.FarmDef
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.TapResult
import dev.moonbag.app.game.UpgradeKind
import dev.moonbag.app.ui.components.FlashText
import dev.moonbag.app.ui.components.Stat
import dev.moonbag.app.ui.formatUsd
import kotlin.math.roundToInt
import kotlin.random.Random

private class Floater(val id: Long, val text: String, val crit: Boolean, val dx: Int)

@Composable
fun MineScreen(
    state: GameState,
    haptics: Boolean,
    onTap: () -> TapResult,
    onBuyUpgrade: (UpgradeKind) -> Boolean,
    onBuyFarm: (Int) -> Boolean,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val u = state.upgrades

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_mine), fontWeight = FontWeight.Bold) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.cash),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    FlashText(
                        text = formatUsd(state.cash),
                        value = state.cash,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        baseColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            stringResource(R.string.per_tap, formatUsd(Clicker.tapValue(u))),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.per_second, formatUsd(Clicker.perSecond(u))),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            TapArea(haptics = haptics, onTap = onTap)

            Text(
                stringResource(R.string.upgrades),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            UpgradeKind.entries.forEach { kind ->
                UpgradeCard(kind, state, onBuy = { onBuyUpgrade(kind) })
            }

            Text(
                stringResource(R.string.farms),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.farms_hint, formatUsd(Clicker.farmIncome(u))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Clicker.FARMS.forEachIndexed { index, farm ->
                FarmCard(index, farm, state, onBuy = { onBuyFarm(index) })
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Stat(stringResource(R.string.taps), state.taps.toString(), Modifier.weight(1f))
                    Stat(stringResource(R.string.earned_total), formatUsd(state.lifetimeEarned), Modifier.weight(1f))
                }
            }
            Box(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TapArea(haptics: Boolean, onTap: () -> TapResult) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var nextId by remember { mutableLongStateOf(0L) }
    val floaters = remember { mutableStateListOf<Floater>() }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tap-scale",
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(secondary.copy(alpha = 0.9f), primary)))
                .pointerInput(haptics) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            val result = onTap()
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            floaters.add(
                                Floater(
                                    id = nextId++,
                                    text = "+" + formatUsd(result.value),
                                    crit = result.crit,
                                    dx = Random.nextInt(-70, 71),
                                ),
                            )
                            tryAwaitRelease()
                            pressed = false
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            MoonArt(Modifier.size(150.dp))
        }

        floaters.forEach { floater ->
            key(floater.id) { FloatingLabel(floater, onDone = { floaters.remove(floater) }) }
        }
    }
}

/** A drawn moon: light disc, soft shade on one side and a few craters. */
@Composable
private fun MoonArt(modifier: Modifier = Modifier) {
    val light = Color(0xFFFFF4D6)
    val shade = Color(0xFFE2CD9A)
    val crater = Color(0xFFD1B97F)
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(Brush.radialGradient(listOf(light, shade), center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.4f), r, c)
        drawCircle(crater, r * 0.18f, Offset(c.x - r * 0.35f, c.y - r * 0.25f))
        drawCircle(crater, r * 0.11f, Offset(c.x + r * 0.30f, c.y - r * 0.40f))
        drawCircle(crater, r * 0.24f, Offset(c.x + r * 0.20f, c.y + r * 0.30f))
        drawCircle(crater, r * 0.08f, Offset(c.x - r * 0.40f, c.y + r * 0.35f))
    }
}

@Composable
private fun FloatingLabel(floater: Floater, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(850, easing = LinearOutSlowInEasing))
        onDone()
    }
    Text(
        text = if (floater.crit) floater.text + " ×5" else floater.text,
        style = if (floater.crit) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = if (floater.crit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .offset { IntOffset(floater.dx, (-progress.value * 150.dp.toPx() - 70.dp.toPx()).roundToInt()) }
            .graphicsLayer { alpha = 1f - progress.value * progress.value },
    )
}

@Composable
private fun UpgradeCard(kind: UpgradeKind, state: GameState, onBuy: () -> Unit) {
    val u = state.upgrades
    val level = Clicker.level(u, kind)
    val maxed = Clicker.isMaxed(u, kind)
    val cost = Clicker.cost(kind, level)
    val next = Clicker.upgraded(u, kind)
    val title: Int
    val icon: ImageVector
    val description: String
    when (kind) {
        UpgradeKind.TAP -> {
            title = R.string.up_tap
            icon = Icons.Filled.TouchApp
            description = stringResource(R.string.up_tap_desc, formatUsd(Clicker.tapValue(u)), formatUsd(Clicker.tapValue(next)))
        }
        UpgradeKind.AUTO -> {
            title = R.string.up_auto
            icon = Icons.Filled.Memory
            description = stringResource(R.string.up_auto_desc, formatUsd(Clicker.perSecond(u)), formatUsd(Clicker.perSecond(next)))
        }
        UpgradeKind.CRIT -> {
            title = R.string.up_crit
            icon = Icons.Filled.AutoAwesome
            description = stringResource(
                R.string.up_crit_desc,
                (Clicker.critChance(u) * 100).roundToInt(),
                (Clicker.critChance(next) * 100).roundToInt(),
                Clicker.CRIT_MULTIPLIER.roundToInt(),
            )
        }
        UpgradeKind.VAULT -> {
            title = R.string.up_vault
            icon = Icons.Filled.Savings
            description = stringResource(R.string.up_vault_desc, Clicker.vaultHours(u), Clicker.vaultHours(next))
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    stringResource(R.string.upgrade_title, stringResource(title), level),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onBuy, enabled = !maxed && state.cash + 1e-9 >= cost) {
                Text(if (maxed) stringResource(R.string.max_level) else formatUsd(cost))
            }
        }
    }
}

private fun farmIcon(key: String): ImageVector = when (key) {
    "memory" -> Icons.Filled.Memory
    "dns" -> Icons.Filled.Dns
    "storage" -> Icons.Filled.Storage
    else -> Icons.Filled.Cloud
}

@Composable
private fun FarmCard(index: Int, farm: FarmDef, state: GameState, onBuy: () -> Unit) {
    val owned = Clicker.farmCount(state.upgrades, index)
    val cost = Clicker.farmCost(index, owned)
    val names = listOf(R.string.farm_gpu, R.string.farm_rig, R.string.farm_dc, R.string.farm_quantum)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(farmIcon(farm.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    stringResource(R.string.farm_title, stringResource(names[index]), owned),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.farm_desc, formatUsd(farm.perSecond), formatUsd(farm.perSecond * owned)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onBuy, enabled = state.cash + 1e-9 >= cost) { Text(formatUsd(cost)) }
        }
    }
}
