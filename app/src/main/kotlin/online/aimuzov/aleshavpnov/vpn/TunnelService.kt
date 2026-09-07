package online.aimuzov.aleshavpnov.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import online.aimuzov.aleshavpnov.App
import online.aimuzov.aleshavpnov.R
import online.aimuzov.aleshavpnov.core.ConfigPreparer
import online.aimuzov.aleshavpnov.core.LibXrayCore
import online.aimuzov.aleshavpnov.core.XrayCore
import online.aimuzov.aleshavpnov.net.SubscriptionFetcher
import online.aimuzov.aleshavpnov.sub.SubscriptionHeaders
import online.aimuzov.aleshavpnov.sub.SubscriptionInfo
import online.aimuzov.aleshavpnov.sub.SubscriptionResponse
import online.aimuzov.aleshavpnov.ui.MainActivity

/**
 * Весь туннель в одном сервисе: подписка → TUN от системы → ядро поверх него.
 * Порядок остановки обратный, и любая ошибка на шаге N откатывает шаги до него.
 */
class TunnelService : VpnService() {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private var job: Job? = null
	private var tun: ParcelFileDescriptor? = null
	private val core: XrayCore = LibXrayCore
	private val app get() = application as App

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		// Always-on VPN будит сервис без intent — это тоже просьба подключиться.
		if (intent?.action == ACTION_STOP) {
			scope.launch { teardown(TunnelState.Idle) }
			return START_NOT_STICKY
		}
		startForeground(getString(R.string.notification_connecting))
		job?.cancel()
		job = scope.launch { connect() }
		return START_STICKY
	}

	private suspend fun connect() {
		val store = app.store
		val url = store.url()
		if (url == null) {
			fail(TunnelError.NoSubscription, null, null)
			return
		}
		set(TunnelState.Connecting("Загружаю подписку"))
		val response =
			runCatching { SubscriptionFetcher(app.identity).fetch(url) }.getOrElse { err ->
				Log.w(TAG, "подписка недоступна, пробую кэш", err)
				val cached = store.cache()
				if (cached == null) {
					fail(TunnelError.SubscriptionUnavailable, err.message, null)
					return
				}
				SubscriptionFetcher.Response(cached.body, decodeHeaders(cached.headersJson))
			}
		val info = SubscriptionHeaders.parse(response.headers)
		val profiles =
			when (val classified = SubscriptionResponse.classify(response.body, response.headers)) {
				is SubscriptionResponse.DeadLink -> {
					fail(TunnelError.DeadLink, classified.announce, info)
					return
				}
				is SubscriptionResponse.Stub -> {
					fail(TunnelError.Stub, classified.text, info)
					return
				}
				is SubscriptionResponse.Profiles -> classified.profiles
			}
		store.saveCache(response.body, encodeHeaders(response.headers))

		set(TunnelState.Connecting("Поднимаю туннель"))
		val pfd =
			runCatching { establishTun() }.getOrNull()
				?: run {
					fail(TunnelError.NoVpnPermission, null, info)
					return
				}
		tun = pfd

		set(TunnelState.Connecting("Запускаю ядро"))
		val configFile = File(filesDir, "config.json")
		var config = ConfigPreparer.prepare(profiles, pfd.fd, MTU)
		withContext(Dispatchers.IO) { configFile.writeText(config.toString()) }
		core.setDns("8.8.8.8:53").onFailure { Log.w(TAG, "setDNS", it) }
		var check = core.test(configFile.path)
		if (check.isFailure) {
			// Форк Happ знает больше ядра: если конфиг не прошёл, пробуем без hysteria.
			Log.w(TAG, "конфиг отвергнут, повторяю без hysteria: ${check.exceptionOrNull()?.message}")
			config = ConfigPreparer.withoutProtocols(config, setOf("hysteria"))
			withContext(Dispatchers.IO) { configFile.writeText(config.toString()) }
			check = core.test(configFile.path)
		}
		val started = check.mapCatching { core.run(configFile.path).getOrThrow() }
		if (started.isFailure) {
			Log.e(TAG, "ядро не поднялось", started.exceptionOrNull())
			closeTun()
			fail(TunnelError.Core, started.exceptionOrNull()?.message, info)
			return
		}
		set(TunnelState.Connected(System.currentTimeMillis(), info))
		updateNotification(getString(R.string.notification_connected) + (info.title?.let { " · $it" } ?: ""))
		Log.i(TAG, "подключено, ядро ${core.version()}")
		watch(info)
	}

	private suspend fun watch(info: SubscriptionInfo) {
		while (true) {
			delay(10_000)
			if (!core.isRunning) {
				Log.e(TAG, "ядро остановилось само")
				teardown(TunnelState.Error(TunnelError.CoreDied, null, info))
				return
			}
		}
	}

	private fun establishTun(): ParcelFileDescriptor? =
		Builder()
			.setSession(getString(R.string.app_name))
			.setMtu(MTU)
			.addAddress("10.0.0.2", 24)
			.addRoute("0.0.0.0", 0)
			.addAddress("fd00::2", 64)
			.addRoute("::", 0)
			.addDnsServer("1.1.1.1")
			.addDisallowedApplication(packageName)
			.setMetered(false)
			.setConfigureIntent(openAppIntent())
			.establish()

	private suspend fun fail(kind: TunnelError, message: String?, info: SubscriptionInfo?) {
		teardown(TunnelState.Error(kind, message, info))
	}

	private suspend fun teardown(final: TunnelState) {
		withContext(Dispatchers.IO) {
			core.stop().onFailure { Log.w(TAG, "stopXray", it) }
			core.resetDns()
			closeTun()
		}
		set(final)
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun closeTun() {
		runCatching { tun?.close() }
		tun = null
	}

	override fun onRevoke() {
		scope.launch { teardown(TunnelState.Error(TunnelError.Revoked, null, null)) }
	}

	override fun onDestroy() {
		job?.cancel()
		runCatching { core.stop() }
		core.resetDns()
		closeTun()
		scope.cancel()
		super.onDestroy()
	}

	private fun set(state: TunnelState) {
		TunnelStateHolder.set(state)
		if (state is TunnelState.Connecting) updateNotification(state.step)
	}

	private fun startForeground(text: String) {
		val type =
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
				ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
			else 0
		ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(text), type)
	}

	private fun updateNotification(text: String) {
		getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
	}

	private fun notification(text: String): Notification {
		val stop =
			PendingIntent.getService(
				this,
				1,
				Intent(this, TunnelService::class.java).setAction(ACTION_STOP),
				PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
			)
		return NotificationCompat.Builder(this, App.CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_launcher_foreground)
			.setContentTitle(getString(R.string.app_name))
			.setContentText(text)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setContentIntent(openAppIntent())
			.addAction(0, getString(R.string.notification_stop), stop)
			.build()
	}

	private fun openAppIntent(): PendingIntent =
		PendingIntent.getActivity(
			this,
			0,
			Intent(this, MainActivity::class.java),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
		)

	private fun encodeHeaders(headers: Map<String, String>): String =
		JsonObject(headers.mapValues { JsonPrimitive(it.value) }).toString()

	private fun decodeHeaders(json: String): Map<String, String> =
		runCatching {
			Json.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content }
		}.getOrDefault(emptyMap())

	companion object {
		const val ACTION_START = "online.aimuzov.aleshavpnov.START"
		const val ACTION_STOP = "online.aimuzov.aleshavpnov.STOP"
		const val TAG = "Alesha"
		private const val NOTIFICATION_ID = 1
		private const val MTU = 1500
	}
}
