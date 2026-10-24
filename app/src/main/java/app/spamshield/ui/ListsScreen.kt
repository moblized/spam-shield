package app.spamshield.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spamshield.core.NumberPattern
import app.spamshield.data.ListEntry
import app.spamshield.data.ListKind

@Composable
fun ListsScreen(vm: MainViewModel, listTab: Int, onTabChange: (Int) -> Unit) {
    val block by vm.blockList.collectAsStateWithLifecycle()
    val allow by vm.allowList.collectAsStateWithLifecycle()
    val entries = if (listTab == 0) block else allow

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            ScreenTitle("Lists")
        }
        TabRow(selectedTabIndex = listTab) {
            Tab(selected = listTab == 0, onClick = { onTabChange(0) }, text = { Text("Blocked (${block.size})") })
            Tab(selected = listTab == 1, onClick = { onTabChange(1) }, text = { Text("Always ring (${allow.size})") })
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        if (listTab == 0) {
                            "No blocked numbers yet. Tap + to add a number, or a pattern like 800* to block every number that starts with 800."
                        } else {
                            "Numbers here always ring, even if a rule would block them. Handy for a doctor's office or delivery driver."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = { it.id }) { entry -> EntryRow(entry) { vm.removeEntry(entry) } }
        }
    }
}

@Composable
private fun EntryRow(entry: ListEntry, onDelete: () -> Unit) {
    val pattern = remember(entry.pattern) { NumberPattern.parse(entry.pattern) }
    val details = listOfNotNull(entry.label, if (pattern?.isWildcard == true) "Pattern" else null)
    val supporting: (@Composable () -> Unit)? = if (details.isEmpty()) null else {
        { Text(details.joinToString(" · ")) }
    }
    ListItem(
        headlineContent = { Text(pattern?.display ?: entry.pattern) },
        supportingContent = supporting,
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
    )
}

@Composable
fun AddEntryDialog(kind: String, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val valid = NumberPattern.parse(number) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == ListKind.BLOCK) "Block a number" else "Always let a number ring") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(if (kind == ListKind.BLOCK) "Number or pattern" else "Number") },
                    singleLine = true,
                    isError = number.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = {
                        Text("* matches any digits, ? matches one. 800* covers every number starting with 800.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(number, label) }, enabled = valid) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
