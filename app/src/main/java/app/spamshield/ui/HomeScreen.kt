@file:OptIn(ExperimentalMaterial3Api::class)

package app.spamshield.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spamshield.core.Action
import app.spamshield.core.PhoneNumbers
import app.spamshield.data.LoggedCall

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onEnableProtection: () -> Unit,
    onOpenDefaultApps: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: return
    val roleHeld by vm.roleHeld.collectAsStateWithLifecycle()
    val roleRequested by vm.roleRequested.collectAsStateWithLifecycle()
    val notificationsAllowed by vm.notificationsAllowed.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val blocked by vm.blockedThisWeek.collectAsStateWithLifecycle()
    val silenced by vm.silencedThisWeek.collectAsStateWithLifecycle()
    val ftcNumbers by vm.ftcNumbers.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LoggedCall?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Spam Shield") }
        item { ProtectionCard(roleHeld, roleRequested, onEnableProtection, onOpenDefaultApps) }
        if (roleHeld && settings.notifyOnBlock && !notificationsAllowed) {
            item { NotificationCard(onRequestNotifications) }
        }
        item { StatsRow(blocked, silenced, ftcNumbers) }
        item { CheckNumberCard(vm) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent calls", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (log.isNotEmpty()) TextButton(onClick = vm::clearLog) { Text("Clear") }
            }
        }
        if (log.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Calls from numbers that aren't in your contacts will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(log, key = { it.id }) { call -> CallRow(call) { selected = call } }
        }
    }

    selected?.let { call -> CallSheet(call, vm) { selected = null } }
}

@Composable
private fun ProtectionCard(
    on: Boolean,
    requested: Boolean,
    onEnable: () -> Unit,
    onOpenDefaultApps: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (on) c.primaryContainer else c.errorContainer,
            contentColor = if (on) c.onPrimaryContainer else c.onErrorContainer,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (on) Icons.Filled.Shield else Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (on) "Protection is on" else "Protection is off",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (on) "Calls from numbers outside your contacts are checked before your phone rings."
                    else "Set Spam Shield as your caller ID & spam app so it can screen calls.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!on) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onEnable) { Text("Turn on") }
                    if (requested) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "If nothing popped up, open Default apps → Caller ID & spam app and pick Spam Shield.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onOpenDefaultApps) { Text("Open default apps") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Notifications, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                "Allow notifications to see when a call is blocked.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequest) { Text("Allow") }
        }
    }
}

@Composable
private fun StatsRow(blocked: Int, silenced: Int, ftcNumbers: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Blocked", "this week", "$blocked", Modifier.weight(1f))
        StatCard("Silenced", "this week", "$silenced", Modifier.weight(1f))
        StatCard("FTC list", "numbers", compact(ftcNumbers), Modifier.weight(1f))
    }
}

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 10_000 -> "${n / 1_000}k"
    n >= 1_000 -> String.format("%.1fk", n / 1_000.0)
    else -> "$n"
}

@Composable
private fun StatCard(title: String, caption: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CheckNumberCard(vm: MainViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val result by vm.checkResult.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Check a number", style = MaterialTheme.typography.titleMedium)
            Text(
                "See what would happen if it called you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("(555) 123-4567") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.check(input) }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { vm.check(input) }, enabled = input.isNotBlank() && !checking) {
                    Text(if (checking) "…" else "Check")
                }
            }
            result?.let { r ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.verdict != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VerdictBadge(r.verdict.action, hypothetical = true)
                            Spacer(Modifier.width(8.dp))
                            Text(r.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(r.message, style = MaterialTheme.typography.bodyMedium)
                    if (r.verdict != null) {
                        Text(
                            "Calls from your contacts always ring normally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRow(call: LoggedCall, onClick: () -> Unit) {
    val style = verdictStyle(actionOf(call.action))
    ListItem(
        headlineContent = { Text(PhoneNumbers.display(call.number), fontWeight = FontWeight.Medium) },
        supportingContent = { Text(call.reason, maxLines = 2) },
        leadingContent = {
            Surface(color = style.container, contentColor = style.content, shape = CircleShape) {
                Icon(
                    style.icon,
                    contentDescription = style.label,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                )
            }
        },
        trailingContent = { Text(relativeTime(call.timestamp), style = MaterialTheme.typography.labelSmall) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun CallSheet(call: LoggedCall, vm: MainViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(PhoneNumbers.display(call.number), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerdictBadge(actionOf(call.action))
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(call.timestamp), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(call.reason, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            val number = call.number
            if (number != null) {
                if (actionOf(call.action) != Action.BLOCK || call.rule != "BLOCK_LIST") {
                    SheetAction(Icons.Filled.Block, "Block this number") { vm.blockNumber(number); onDismiss() }
                }
                SheetAction(Icons.Filled.CheckCircle, "Always let this number ring") { vm.allowNumber(number); onDismiss() }
                SheetAction(Icons.Filled.ContentCopy, "Copy number") {
                    clipboard.setText(AnnotatedString(number))
                    onDismiss()
                }
            }
            SheetAction(Icons.Filled.Delete, "Remove from log") { vm.deleteLog(call); onDismiss() }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    )
}
