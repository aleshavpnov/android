package online.aimuzov.aleshavpnov.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Кнопка → сервис. Единственная нетривиальность — системный диалог согласия на VPN. */
class VpnController(private val activity: ComponentActivity) {
	private val consent =
		activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) launch()
			else TunnelStateHolder.set(TunnelState.Error(TunnelError.NoVpnPermission, null, null))
		}

	fun start() {
		val ask = VpnService.prepare(activity)
		if (ask == null) launch() else consent.launch(ask)
	}

	fun stop() {
		activity.startService(Intent(activity, TunnelService::class.java).setAction(TunnelService.ACTION_STOP))
	}

	private fun launch() {
		ContextCompat.startForegroundService(
			activity,
			Intent(activity, TunnelService::class.java).setAction(TunnelService.ACTION_START),
		)
	}
}
