@file:OptIn(ExperimentalMaterial3Api::class)

package app.spamshield.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spamshield.core.Action
import app.spamshield.core.NeighborMode
import app.spamshield.core.PhoneNumbers
import app.spamshield.core.Rule
import app.spamshield.data.AppSettings

@Composable
fun RulesScreen(vm: MainViewModel) {
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle(
                "Rules",
                "Silence lets a call ring quietly so you can still see it. Block hangs up and leaves it in your call history.",
            )
        }
        items(Rule.entries.toList(), key = { it.name }) { rule ->
            RuleCard(rule, settings.action(rule), onAction = { vm.setAction(rule, it) }) {
                when (rule) {
                    Rule.FTC -> FtcOptions(settings, vm)
                    Rule.NEIGHBOR -> NeighborOptions(settings, vm)
                    Rule.COMMUNITY -> CommunityOptions(settings, vm)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, action: Action, onAction: (Action) -> Unit, extras: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(rule.title, style = MaterialTheme.typography.titleMedium)
            Text(
                rule.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionSelector(action, onAction)
            if (action != Action.OFF && rule in setOf(Rule.FTC, Rule.NEIGHBOR, Rule.COMMUNITY)) {
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                extras()
            }
        }
    }
}

@Composable
private fun FtcOptions(settings: AppSettings, vm: MainViewModel) {
    val progress by vm.ftcProgress.collectAsStateWithLifecycle()
    val numbers by vm.ftcNumbers.collectAsStateWithLifecycle()

    Stepper("Reports needed", settings.ftcMinReports, 1..10, vm::setFtcMinReports)
    Text("Use reports from the last", style = MaterialTheme.typography.bodyMedium)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(7, 14, 30, 60, 90).forEach { days ->
            FilterChip(
                selected = settings.ftcDays == days,
                onClick = { vm.setFtcDays(days) },
                label = { Text("$days days") },
            )
        }
    }
    val status = progress ?: if (settings.ftcUpdatedAt == 0L) {
        "Not downloaded yet"
    } else {
        String.format("%,d numbers on your phone · updated %s", numbers, relativeTime(settings.ftcUpdatedAt))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = vm::syncFtcNow, enabled = progress == null) { Text("Update now") }
    }
    if (settings.ftcError.isNotBlank() && progress == null) {
        Text(settings.ftcError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun NeighborOptions(settings: AppSettings, vm: MainViewModel) {
    var number by rememberSaveable { mutableStateOf(settings.myNumber) }
    val valid = PhoneNumbers.normalize(number)?.let { PhoneNumbers.isNanp(it) } == true

    OutlinedTextField(
        value = number,
        onValueChange = {
            number = it
            vm.setMyNumber(it)
        },
        label = { Text("Your phone number") },
        singleLine = true,
        isError = number.isNotBlank() && !valid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        supportingText = {
            Text(
                when {
                    number.isBlank() -> "Needed to spot look-alike numbers. It stays on this phone."
                    valid -> "Stays on this phone."
                    else -> "Enter your 10-digit number."
                }
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Catch calls that share your", style = MaterialTheme.typography.bodyMedium)
    ChoiceRow(
        options = listOf(
            NeighborMode.SAME_PREFIX to "Area code + 3",
            NeighborMode.SAME_AREA_CODE to "Area code",
        ),
        selected = settings.neighborMode,
        onSelect = vm::setNeighborMode,
    )
    Text(
        if (settings.neighborMode == NeighborMode.SAME_PREFIX) {
            "Matches numbers like yours, e.g. 704-555-xxxx when you're 704-555-0199."
        } else {
            "Matches every unknown caller in your area code. Stricter, but local businesses get caught too."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CommunityOptions(settings: AppSettings, vm: MainViewModel) {
    var token by rememberSaveable { mutableStateOf(settings.communityToken) }

    Stepper("Reports needed", settings.communityMinVotes, 1..20, vm::setCommunityMinVotes)
    OutlinedTextField(
        value = token,
        onValueChange = {
            token = it
            vm.setCommunityToken(it)
        },
        label = { Text("PhoneBlock API key (optional)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "When an unknown number calls, it's sent to phoneblock.net to be checked. If the lookup takes too long, the call rings through.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
