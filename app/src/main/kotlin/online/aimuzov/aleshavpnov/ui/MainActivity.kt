package online.aimuzov.aleshavpnov.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import online.aimuzov.aleshavpnov.App
import online.aimuzov.aleshavpnov.BuildConfig
import online.aimuzov.aleshavpnov.deeplink.AddLink
import online.aimuzov.aleshavpnov.update.UpdateChecker
import online.aimuzov.aleshavpnov.update.UpdateInstaller
import online.aimuzov.aleshavpnov.update.VersionInfo
import online.aimuzov.aleshavpnov.vpn.TunnelStateHolder
import online.aimuzov.aleshavpnov.vpn.VpnController

class MainActivity : ComponentActivity() {
	private lateinit var controller: VpnController
	private val app get() = application as App
	private val update = MutableStateFlow<VersionInfo?>(null)

	// Уведомление — единственное окно в сервис, когда приложение свёрнуто;
	// без права его показывать foreground-сервис на Android 13+ работает молча.
	private val notifications =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		controller = VpnController(this)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
		}
		setContent {
			AppTheme {
				MainScreen(
					state = TunnelStateHolder.state,
					store = app.store,
					update = update,
					onConnect = controller::start,
					onDisconnect = controller::stop,
					onOpenCabinet = ::openCabinet,
					onInstallUpdate = ::installUpdate,
				)
			}
		}
		handleLink(intent)
		lifecycleScope.launch {
			update.value = UpdateChecker(app.store, BuildConfig.VERSION_CODE).check()
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleLink(intent)
	}

	/** Диплинк из кабинета: сохранили ссылку — и сразу подключаемся, без лишнего тапа. */
	private fun handleLink(intent: Intent?) {
		val data = intent?.dataString ?: return
		val url = AddLink.parse(data)
		if (url == null) {
			Toast.makeText(this, "Ссылка не распознана", Toast.LENGTH_LONG).show()
			return
		}
		lifecycleScope.launch {
			app.store.saveUrl(url)
			Toast.makeText(this@MainActivity, "Подписка добавлена", Toast.LENGTH_SHORT).show()
			controller.start()
		}
	}

	private fun openCabinet() {
		startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.CABINET_URL)))
	}

	private fun installUpdate() {
		val info = update.value ?: return
		lifecycleScope.launch {
			UpdateInstaller(this@MainActivity).install(info).onFailure {
				Toast.makeText(this@MainActivity, "Обновление не установилось: ${it.message}", Toast.LENGTH_LONG).show()
			}
		}
	}
}
