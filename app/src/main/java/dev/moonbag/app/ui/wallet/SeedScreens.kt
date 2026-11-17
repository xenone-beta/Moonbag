@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.moonbag.app.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.Wallet
import dev.moonbag.app.game.WalletInfo

/** The twelve words in a numbered grid; hidden until the user chooses to show them. */
@Composable
private fun WordGrid(words: List<String>, visible: Boolean) {
    Column(Modifier.fillMaxWidth().alpha(if (visible) 1f else 0.08f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in words.chunked(2).withIndex()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, w) in row.value.withIndex()) {
                    val number = row.index * 2 + i + 1
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        Text(if (visible) w else "••••••", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * First run of the wallet: make a new one or restore one from twelve words.
 * [onCreate] makes the words (they then arrive through [wallet]); [onImport] returns false for bad words.
 */
@Composable
fun WalletSetupScreen(
    wallet: WalletInfo,
    onCreate: () -> Unit,
    onImport: (String) -> Boolean,
    onBackedUp: () -> Unit,
    onFinish: () -> Unit,
) {
    // 0 welcome, 1 new words, 2 import
    var step by rememberSaveable { mutableIntStateOf(0) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                step == 1 && wallet.exists -> {
                    Text(stringResource(R.string.seed_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.seed_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    WordGrid(wallet.words, visible)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, Modifier.size(18.dp))
                            Text(stringResource(if (visible) R.string.seed_hide else R.string.seed_show), Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(wallet.words.joinToString(" "))) }, enabled = visible) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.copy), Modifier.padding(start = 6.dp))
                        }
                    }
                    GameNote()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saved, onCheckedChange = { saved = it })
                        Text(stringResource(R.string.seed_saved_check), Modifier.weight(1f))
                    }
                    Button(onClick = { onBackedUp(); onFinish() }, enabled = saved, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.continue_label))
                    }
                }
                step == 2 -> {
                    Text(stringResource(R.string.import_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.import_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it; failed = false },
                        label = { Text(stringResource(R.string.import_hint)) },
                        isError = failed,
                        supportingText = { if (failed) Text(stringResource(R.string.import_bad)) },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.import_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { if (onImport(typed)) onFinish() else failed = true },
                        enabled = typed.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(stringResource(R.string.import_action)) }
                    TextButton(onClick = { step = 0; failed = false }) { Text(stringResource(R.string.back)) }
                }
                else -> {
                    Spacer(Modifier.height(32.dp))
                    Box(
                        Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.AccountBalanceWallet, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    GameNote()
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onCreate(); visible = false; saved = false; step = 1 }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.create_wallet))
                    }
                    OutlinedButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.import_wallet))
                    }
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.later)) }
                }
            }
        }
    }
}

@Composable
private fun GameNote() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, null, Modifier.size(20.dp))
            Text(stringResource(R.string.seed_game_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** Shows the recovery phrase of an existing wallet, after a tap to reveal. */
@Composable
fun SeedPhraseScreen(wallet: WalletInfo, onConfirmBackup: () -> Unit, onBack: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Scaffold(topBar = { WalletTopBar(stringResource(R.string.seed_screen_title), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!wallet.exists) {
                Text(stringResource(R.string.no_wallet_body))
            } else {
                Text(stringResource(R.string.seed_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WordGrid(wallet.words, visible)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, Modifier.size(18.dp))
                        Text(stringResource(if (visible) R.string.seed_hide else R.string.seed_show), Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(wallet.words.joinToString(" "))) }, enabled = visible) {
                        Text(stringResource(R.string.copy))
                    }
                }
                GameNote()
                if (!wallet.backedUp) {
                    Button(onClick = onConfirmBackup, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.seed_mark_saved)) }
                } else {
                    Text(stringResource(R.string.seed_is_saved), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
