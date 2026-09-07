package online.aimuzov.aleshavpnov

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import online.aimuzov.aleshavpnov.data.DeviceIdentity
import online.aimuzov.aleshavpnov.data.SubscriptionStore

class App : Application() {
	lateinit var store: SubscriptionStore
		private set
	lateinit var identity: DeviceIdentity
		private set

	override fun onCreate() {
		super.onCreate()
		store = SubscriptionStore(this)
		identity = DeviceIdentity(this, store)
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL_ID,
				getString(R.string.notification_channel),
				NotificationManager.IMPORTANCE_LOW,
			),
		)
	}

	companion object {
		const val CHANNEL_ID = "tunnel"
	}
}
