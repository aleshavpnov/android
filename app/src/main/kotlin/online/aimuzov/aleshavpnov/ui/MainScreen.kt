package online.aimuzov.aleshavpnov.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import online.aimuzov.aleshavpnov.data.SubscriptionStore
import online.aimuzov.aleshavpnov.sub.SubscriptionInfo
import online.aimuzov.aleshavpnov.update.VersionInfo
import online.aimuzov.aleshavpnov.vpn.TunnelError
import online.aimuzov.aleshavpnov.vpn.TunnelState
import java.text.DateFormat
import java.util.Date

@Composable
fun MainScreen(
	state: StateFlow<TunnelState>,
	store: SubscriptionStore,
	update: StateFlow<VersionInfo?>,
	onConnect: () -> Unit,
	onDisconnect: () -> Unit,
	onInstallUpdate: () -> Unit,
) {
	val current by state.collectAsStateWithLifecycle()
	val hasSubscription by store.hasUrl.collectAsStateWithLifecycle(initialValue = false)
	val available by update.collectAsStateWithLifecycle()

	Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
		Column(
			modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
		) {
			PowerButton(
				state = current,
				enabled = hasSubscription || current is TunnelState.Connected,
				onConnect = onConnect,
				onDisconnect = onDisconnect,
			)
			Spacer(Modifier.height(24.dp))
			Text(
				text = statusText(current, hasSubscription),
				style = MaterialTheme.typography.bodyLarge,
				textAlign = TextAlign.Center,
			)
			InfoBlock(current)
			ErrorBlock(current, onRetry = onConnect)
			available?.let { v ->
				Spacer(Modifier.height(24.dp))
				Button(onClick = onInstallUpdate) { Text("Обновить до ${v.versionName}") }
			}
		}
	}
}

/** Одна кнопка на весь экран. Цвет — и есть статус: серый, жёлтый, зелёный, красный. */
@Composable
private fun PowerButton(
	state: TunnelState,
	enabled: Boolean,
	onConnect: () -> Unit,
	onDisconnect: () -> Unit,
) {
	val color =
		when (state) {
			is TunnelState.Connected -> Green
			is TunnelState.Connecting -> Amber
			is TunnelState.Error -> Red
			TunnelState.Idle -> if (enabled) Color(0xFF64748B) else Color(0xFFCBD5E1)
		}
	val label =
		when (state) {
			is TunnelState.Connected -> "Выключить"
			is TunnelState.Connecting -> "Подключаю…"
			else -> "Включить"
		}
	Box(
		modifier =
			Modifier.size(220.dp)
				.shadow(if (enabled) 12.dp else 0.dp, CircleShape)
				.background(color, CircleShape)
				.clickable(enabled = enabled && state !is TunnelState.Connecting) {
					if (state is TunnelState.Connected) onDisconnect() else onConnect()
				},
		contentAlignment = Alignment.Center,
	) {
		Text(text = label, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
	}
}

private fun statusText(state: TunnelState, hasSubscription: Boolean): String =
	when (state) {
		TunnelState.Idle ->
			if (hasSubscription) "VPN выключен"
			else "Подписки пока нет. Откройте кабинет в Telegram и нажмите «Открыть в приложении»."
		is TunnelState.Connecting -> state.step
		is TunnelState.Connected -> "VPN включён"
		is TunnelState.Error -> errorTitle(state.kind)
	}

private fun errorTitle(kind: TunnelError): String =
	when (kind) {
		TunnelError.NoSubscription -> "Подписка не добавлена"
		TunnelError.NoVpnPermission -> "Нужно разрешение на VPN"
		TunnelError.SubscriptionUnavailable -> "Не удалось загрузить подписку"
		TunnelError.DeadLink -> "Ссылка устарела"
		TunnelError.Stub -> "Доступ ограничен"
		TunnelError.Core -> "Ядро отвергло конфиг"
		TunnelError.CoreDied -> "Соединение оборвалось"
		TunnelError.Revoked -> "VPN отключила система"
	}

@Composable
private fun InfoBlock(state: TunnelState) {
	val info: SubscriptionInfo? =
		when (state) {
			is TunnelState.Connected -> state.info
			is TunnelState.Error -> state.info
			else -> null
		}
	if (info == null) return
	Spacer(Modifier.height(16.dp))
	info.userinfo?.let { u ->
		val expire = u.expire?.takeIf { it > 0 }?.let { Date(it * 1000) }
		val expired = expire != null && expire.before(Date())
		val used = (u.upload ?: 0) + (u.download ?: 0)
		val parts = buildList {
			if (u.total != null && u.total > 0) add("${gb(used)} из ${gb(u.total)} ГБ")
			if (expire != null) add((if (expired) "истекла " else "до ") + DateFormat.getDateInstance(DateFormat.MEDIUM).format(expire))
		}
		if (parts.isNotEmpty()) {
			Text(
				parts.joinToString(" · "),
				style = MaterialTheme.typography.bodySmall,
				color = if (expired) Red else MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
	info.announce?.let {
		Spacer(Modifier.height(12.dp))
		Text(
			it,
			style = MaterialTheme.typography.bodySmall,
			textAlign = TextAlign.Center,
			modifier =
				Modifier.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
					.padding(12.dp),
		)
	}
}

@Composable
private fun ErrorBlock(state: TunnelState, onRetry: () -> Unit) {
	if (state !is TunnelState.Error) return
	Spacer(Modifier.height(16.dp))
	// У dead-link и заглушки текст ошибки и есть announce — он уже показан выше.
	state.message?.takeIf { it != state.info?.announce }?.let {
		Text(
			it,
			style = MaterialTheme.typography.bodySmall,
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(Modifier.height(12.dp))
	}
	when (state.kind) {
		TunnelError.NoSubscription -> {}
		else -> Button(onClick = onRetry) { Text("Повторить") }
	}
}

private fun gb(bytes: Long): String = "%.1f".format(bytes / 1_073_741_824.0)
