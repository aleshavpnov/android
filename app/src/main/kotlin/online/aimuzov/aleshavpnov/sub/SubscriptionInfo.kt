package online.aimuzov.aleshavpnov.sub

import kotlinx.serialization.Serializable

/** Строка состояния подписки из `subscription-userinfo`: байты и unix-время окончания. */
data class UserInfo(
	val upload: Long?,
	val download: Long?,
	val total: Long?,
	val expire: Long?,
)

/** Карточка тарифа из `x-alesha-plan`; любое поле может отсутствовать (VIP, старый бот). */
@Serializable
data class PlanInfo(
	val plan: String? = null,
	val deviceLimit: Int? = null,
	val devices: Int? = null,
	val usedBytes: Long? = null,
	val expiresAt: Long? = null,
)

/** Что подписка рассказала о себе заголовками — показываем под кнопкой. */
data class SubscriptionInfo(
	val title: String?,
	val announce: String?,
	val userinfo: UserInfo?,
	val plan: PlanInfo? = null,
)
