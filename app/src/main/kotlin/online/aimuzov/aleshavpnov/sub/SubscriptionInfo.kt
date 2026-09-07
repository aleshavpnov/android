package online.aimuzov.aleshavpnov.sub

/** Строка состояния подписки из `subscription-userinfo`: байты и unix-время окончания. */
data class UserInfo(
	val upload: Long?,
	val download: Long?,
	val total: Long?,
	val expire: Long?,
)

/** Что подписка рассказала о себе заголовками — показываем под кнопкой. */
data class SubscriptionInfo(
	val title: String?,
	val announce: String?,
	val userinfo: UserInfo?,
)
