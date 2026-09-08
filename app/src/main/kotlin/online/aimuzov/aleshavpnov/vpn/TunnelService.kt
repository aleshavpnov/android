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
import kotlinx.serialization.json.JsonArray
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
	private var connectedAt = 0L
	private var lastCoreError: String? = null

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
		// Второй диплинк при живом туннеле — это просьба переподключиться со свежей ссылкой.
		// Ядро в процессе одно, и без остановки старого runXray отвечает «already running».
		release()
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
				SubscriptionFetcher.Response(cached.body, cached.headers)
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
		store.saveCache(response.body, response.headers)

		set(TunnelState.Connecting("Поднимаю туннель"))
		val pfd =
			runCatching { establishTun() }.getOrNull()
				?: run {
					fail(TunnelError.NoVpnPermission, null, info)
					return
				}
		tun = pfd

		set(TunnelState.Connecting("Запускаю ядро"))
		if (startCore(profiles, pfd.fd).isFailure) {
			closeTun()
			fail(TunnelError.Core, lastCoreError, info)
			return
		}
		connectedAt = System.currentTimeMillis()
		set(TunnelState.Connected(connectedAt, info))
		updateNotification(getString(R.string.notification_connected))
		Log.i(TAG, "подключено, ядро ${core.version()}")
		watch(info, response.body)
	}

	/**
	 * Пока туннель жив: следим за ядром и раз в час перечитываем подписку. Профиль меняется
	 * на стороне бота (новые ноды, тумблеры провайдеров), и клиент должен подхватывать это сам,
	 * без ручного обновления — иначе человек сидит на устаревшем каскаде до перезапуска.
	 */
	private suspend fun watch(info: SubscriptionInfo, body: String) {
		var lastBody = body
		var sinceRefresh = 0L
		while (true) {
			delay(TICK_MS)
			if (!core.isRunning) {
				Log.e(TAG, "ядро остановилось само")
				teardown(TunnelState.Error(TunnelError.CoreDied, null, info))
				return
			}
			sinceRefresh += TICK_MS
			if (sinceRefresh < REFRESH_MS) continue
			sinceRefresh = 0L
			val url = app.store.url() ?: continue
			val fresh =
				runCatching { SubscriptionFetcher(app.identity).fetch(url) }.getOrElse { err ->
					Log.w(TAG, "фоновое обновление подписки не удалось", err)
					continue
				}
			val classified = SubscriptionResponse.classify(fresh.body, fresh.headers)
			if (classified !is SubscriptionResponse.Profiles) {
				// Ссылку отозвали или упёрлись в лимит устройств — рвать канал молча нельзя,
				// показываем причину и гасим туннель.
				val refreshed = SubscriptionHeaders.parse(fresh.headers)
				val (kind, text) =
					when (classified) {
						is SubscriptionResponse.DeadLink -> TunnelError.DeadLink to classified.announce
						is SubscriptionResponse.Stub -> TunnelError.Stub to classified.text
						else -> TunnelError.SubscriptionUnavailable to null
					}
				teardown(TunnelState.Error(kind, text, refreshed))
				return
			}
			app.store.saveCache(fresh.body, fresh.headers)
			TunnelStateHolder.set(TunnelState.Connected(connectedAt, SubscriptionHeaders.parse(fresh.headers)))
			if (fresh.body == lastBody) continue
			Log.i(TAG, "подписка изменилась — перезапускаю ядро")
			lastBody = fresh.body
			// Пересобираем конфиг на том же tun: интерфейс системе не пересоздаём, иначе
			// у всех приложений оборвутся соединения.
			val fd = tun?.fd ?: return
			core.stop().onFailure { Log.w(TAG, "stopXray перед перезапуском", it) }
			if (startCore(classified.profiles, fd).isFailure) {
				teardown(TunnelState.Error(TunnelError.Core, null, info))
				return
			}
		}
	}

	/**
	 * Пишет конфиг и поднимает ядро. Если конфиг не принят, второй заход идёт без hysteria:
	 * этот протокол есть только в форке Happ, ванильное ядро на нём падает при старте.
	 */
	private suspend fun startCore(profiles: JsonArray, fd: Int): Result<Unit> =
		withContext(Dispatchers.IO) {
			val configFile = File(filesDir, "config.json")
			var config = ConfigPreparer.prepare(profiles, fd, MTU)
			configFile.writeText(config.toString())
			core.setDns("8.8.8.8:53").onFailure { Log.w(TAG, "setDNS", it) }
			var check = core.test(configFile.path)
			if (check.isFailure) {
				Log.w(TAG, "конфиг отвергнут, повторяю без hysteria: ${check.exceptionOrNull()?.message}")
				config = ConfigPreparer.withoutProtocols(config, setOf("hysteria"))
				configFile.writeText(config.toString())
				check = core.test(configFile.path)
			}
			val started = check.mapCatching { core.run(configFile.path).getOrThrow() }
			lastCoreError = started.exceptionOrNull()?.message
			started.onFailure { Log.e(TAG, "ядро не поднялось", it) }
			started
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
		release()
		set(final)
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	/** Освобождает ядро и интерфейс, ничего не сообщая экрану: состояние ставит вызывающий. */
	private suspend fun release() {
		withContext(Dispatchers.IO) {
			core.stop().onFailure { Log.w(TAG, "stopXray", it) }
			core.resetDns()
			closeTun()
		}
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

	companion object {
		const val ACTION_START = "online.aimuzov.aleshavpnov.START"
		const val ACTION_STOP = "online.aimuzov.aleshavpnov.STOP"
		const val TAG = "Alesha"
		private const val NOTIFICATION_ID = 1
		private const val MTU = 1500
		private const val TICK_MS = 10_000L
		private const val REFRESH_MS = 60 * 60 * 1000L
	}
}
