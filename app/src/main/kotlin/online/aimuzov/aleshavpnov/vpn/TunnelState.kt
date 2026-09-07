package online.aimuzov.aleshavpnov.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import online.aimuzov.aleshavpnov.sub.SubscriptionInfo

/** Почему туннель не поднялся — экран по этому выбирает текст и кнопку действия. */
enum class TunnelError {
	/** Ссылки-подписки ещё нет: приложение открыли с иконки, а не из кабинета. */
	NoSubscription,
	/** Человек не дал разрешение на VPN в системном диалоге. */
	NoVpnPermission,
	/** Подписку не удалось скачать, кэша тоже нет. */
	SubscriptionUnavailable,
	/** Бот ответил, что ссылка мёртвая — нужна свежая из кабинета. */
	DeadLink,
	/** Бот отдал заглушку вместо серверов (лимит устройств, приостановка). */
	Stub,
	/** Ядро отвергло конфиг или упало на старте. */
	Core,
	/** Ядро работало и остановилось само. */
	CoreDied,
	/** Система отозвала VPN (включили другой VPN). */
	Revoked,
}

sealed interface TunnelState {
	data object Idle : TunnelState

	data class Connecting(val step: String) : TunnelState

	data class Connected(val sinceMillis: Long, val info: SubscriptionInfo?) : TunnelState

	data class Error(val kind: TunnelError, val message: String?, val info: SubscriptionInfo?) :
		TunnelState
}

/**
 * Единственный источник состояния для экрана и уведомления. Сервис пишет, все остальные
 * читают; процесс один, поэтому object без IPC.
 */
object TunnelStateHolder {
	private val mutable = MutableStateFlow<TunnelState>(TunnelState.Idle)
	val state: StateFlow<TunnelState> = mutable.asStateFlow()

	fun set(value: TunnelState) {
		mutable.value = value
	}
}
