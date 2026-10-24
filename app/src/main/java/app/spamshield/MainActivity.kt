package app.spamshield

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spamshield.data.ThemeMode
import app.spamshield.ui.AppRoot
import app.spamshield.ui.MainViewModel
import app.spamshield.ui.theme.SpamShieldTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshRole()
        vm.markRoleRequested()
        if (vm.roleHeld.value) requestNotifications()
    }

    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            SpamShieldTheme(settings?.theme ?: ThemeMode.SYSTEM) {
                AppRoot(
                    vm = vm,
                    onEnableProtection = ::requestRole,
                    onOpenDefaultApps = ::openDefaultApps,
                    onRequestNotifications = ::requestNotifications,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshRole()
        vm.refreshPermissions()
    }

    private fun requestRole() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        } else {
            openDefaultApps()
        }
    }

    private fun openDefaultApps() {
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
