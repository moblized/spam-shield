package app.spamshield.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.spamshield.data.ListKind

private data class Destination(val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("Home", Icons.Filled.Shield),
    Destination("Rules", Icons.Filled.Tune),
    Destination("Lists", Icons.AutoMirrored.Filled.List),
    Destination("Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(
    vm: MainViewModel,
    onEnableProtection: () -> Unit,
    onOpenDefaultApps: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var listTab by rememberSaveable { mutableIntStateOf(0) }
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 2) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add a number")
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> HomeScreen(vm, onEnableProtection, onOpenDefaultApps, onRequestNotifications)
                1 -> RulesScreen(vm)
                2 -> ListsScreen(vm, listTab) { listTab = it }
                else -> SettingsScreen(vm)
            }
        }
    }

    if (adding) {
        val kind = if (listTab == 0) ListKind.BLOCK else ListKind.ALLOW
        AddEntryDialog(
            kind = kind,
            onDismiss = { adding = false },
            onAdd = { number, label ->
                vm.addEntry(kind, number, label)
                adding = false
            },
        )
    }
}
