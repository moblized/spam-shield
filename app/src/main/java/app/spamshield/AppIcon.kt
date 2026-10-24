package app.spamshield

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import app.spamshield.data.ThemeMode

/**
 * Swaps the launcher icon between the light and dark versions to match the app's theme.
 * Called when the app goes to the background, because switching the launcher entry
 * while the app is on screen can close it on some phones.
 */
object AppIcon {
    private const val LIGHT = "app.spamshield.LauncherLight"
    private const val DARK = "app.spamshield.LauncherDark"

    fun wantsDark(context: Context, theme: ThemeMode): Boolean = when (theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
        ThemeMode.SYSTEM ->
            (context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    fun sync(context: Context, theme: ThemeMode) {
        val dark = wantsDark(context, theme)
        val pm = context.packageManager
        val light = ComponentName(context.packageName, LIGHT)
        val darkAlias = ComponentName(context.packageName, DARK)
        val wanted = if (dark) darkAlias else light
        val other = if (dark) light else darkAlias

        // Leave it alone when it already matches, so the launcher isn't disturbed.
        if (isEnabled(pm, wanted) && !isEnabled(pm, other)) return

        runCatching {
            // Turn the new one on first so there's always an icon to tap.
            pm.setComponentEnabledSetting(wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(other, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    private fun isEnabled(pm: PackageManager, component: ComponentName): Boolean =
        when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> component.className == LIGHT
            else -> false
        }
}
