package app.spamshield.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spamshield.data.ThemeMode

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Settings") }
        item {
            Card(colors = cardColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    ChoiceRow(
                        options = listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.DARK to "Dark",
                            ThemeMode.BLACK to "Black",
                        ),
                        selected = settings.theme,
                        onSelect = vm::setTheme,
                    )
                }
            }
        }
        item {
            Card(colors = cardColors) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SwitchRow(
                        "Notify when a call is blocked",
                        "Shows who was blocked and why, with a Not spam button.",
                        settings.notifyOnBlock,
                        vm::setNotifyOnBlock,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SwitchRow(
                        "Show calls that rang in the log",
                        "Keeps unknown callers that weren't blocked on the Home tab, so you can block them later.",
                        settings.logAllowed,
                        vm::setLogAllowed,
                    )
                }
            }
        }
        item {
            Card(colors = cardColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Android hands Spam Shield every incoming call from a number that isn't in your contacts. " +
                            "It checks your lists and rules in a split second and tells the phone to ring, ring silently, or hang up. " +
                            "Calls from your contacts never reach it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "The FTC list is the Do Not Call complaint data the FTC publishes every weekday. Reports aren't verified, " +
                            "so a number needs a few reports before it's blocked, and blocked calls still show in your call history.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Spam Shield only screens calls. Your texts are untouched.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Text(
                "Spam Shield $version",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
