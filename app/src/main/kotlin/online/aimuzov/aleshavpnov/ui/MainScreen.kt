package online.aimuzov.aleshavpnov.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import online.aimuzov.aleshavpnov.R
import online.aimuzov.aleshavpnov.data.SubscriptionStore
import online.aimuzov.aleshavpnov.sub.SubscriptionInfo
import online.aimuzov.aleshavpnov.update.VersionInfo
import online.aimuzov.aleshavpnov.vpn.TunnelError
import online.aimuzov.aleshavpnov.vpn.TunnelState

private val RU = Locale("ru")
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Кольцо вокруг индикатора: по нему видно состояние ещё до чтения заголовка. */
private enum class Ring { None, Spinner, Glow, Dashed, Alert }

private enum class Primary { None, OpenCabinet }

/** Всё, что экран рисует, выведено из состояния туннеля один раз — вёрстка ниже только раскладывает. */
private data class Presentation(
	val title: String,
	val subtitle: String,
	val circle: Color,
	val ring: Ring,
	val icon: Int,
	val dot: Color,
	val timer: Boolean,
	val expired: Boolean,
	val card: Boolean,
	val clickable: Boolean,
	val primary: Primary,
	val secondary: String?,
)

@Composable
fun MainScreen(
	state: StateFlow<TunnelState>,
	store: SubscriptionStore,
	update: StateFlow<VersionInfo?>,
	onConnect: () -> Unit,
	onDisconnect: () -> Unit,
	onOpenCabinet: () -> Unit,
	onInstallUpdate: () -> Unit,
) {
	val current by state.collectAsStateWithLifecycle()
	val hasSubscription by store.hasUrl.collectAsStateWithLifecycle(initialValue = false)
	val cached by store.cachedInfo.collectAsStateWithLifecycle(initialValue = null)
	val available by update.collectAsStateWithLifecycle()
	val palette = LocalPalette.current

	val info: SubscriptionInfo? =
		when (current) {
			is TunnelState.Connected -> (current as TunnelState.Connected).info
			is TunnelState.Error -> (current as TunnelState.Error).info ?: cached
			else -> cached
		}
	val p = presentation(current, hasSubscription, palette)

	Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
		// Скролл на случай маленького экрана: центрирование остаётся, пока всё влезает.
		Column(
			modifier =
				Modifier.fillMaxSize()
					.systemBarsPadding()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp, vertical = 24.dp),
			verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
		) {
			Header(p.title, p.subtitle)
			Indicator(
				presentation = p,
				since = (current as? TunnelState.Connected)?.sinceMillis,
				onClick = { if (current is TunnelState.Connected) onDisconnect() else onConnect() },
			)
			if (p.card && info != null && (info.userinfo != null || info.plan != null)) {
				PlanCard(info, dot = p.dot, expired = p.expired)
			}
			Actions(
				presentation = p,
				update = available,
				onOpenCabinet = onOpenCabinet,
				onRetry = onConnect,
				onInstallUpdate = onInstallUpdate,
			)
		}
	}
}

@Composable
private fun presentation(state: TunnelState, hasSubscription: Boolean, palette: AppPalette): Presentation {
	val noSubscription =
		Presentation(
			title = "Подписки пока нет",
			subtitle = "Откройте кабинет в Telegram и нажмите «Открыть в приложении».",
			circle = palette.idle,
			ring = Ring.Dashed,
			icon = R.drawable.ic_power,
			dot = palette.muted,
			timer = false,
			expired = false,
			card = false,
			clickable = false,
			primary = Primary.OpenCabinet,
			secondary = null,
		)
	return when (state) {
		TunnelState.Idle ->
			if (!hasSubscription) noSubscription
			else
				Presentation(
					title = "VPN выключен",
					subtitle = "Подписка добавлена. Нажмите «Включить» — остальное приложение сделает само.",
					circle = palette.idle,
					ring = Ring.None,
					icon = R.drawable.ic_power,
					dot = palette.muted,
					timer = false,
					expired = false,
					card = true,
					clickable = true,
					primary = Primary.None,
					secondary = null,
				)
		is TunnelState.Connecting ->
			Presentation(
				title = "Подключаю…",
				subtitle = state.step,
				circle = Amber,
				ring = Ring.Spinner,
				icon = R.drawable.ic_power,
				dot = Amber,
				timer = false,
				expired = false,
				card = true,
				clickable = false,
				primary = Primary.None,
				secondary = null,
			)
		is TunnelState.Connected ->
			Presentation(
				title = "Защищено",
				subtitle = "Весь трафик идёт через защищённый канал.",
				circle = Emerald,
				ring = Ring.Glow,
				icon = R.drawable.ic_zap,
				dot = Emerald,
				timer = true,
				expired = false,
				card = true,
				clickable = true,
				primary = Primary.None,
				secondary = null,
			)
		is TunnelState.Error ->
			when (state.kind) {
				TunnelError.NoSubscription -> noSubscription
				TunnelError.DeadLink, TunnelError.Stub ->
					Presentation(
						title = if (state.kind == TunnelError.DeadLink) "Ссылка устарела" else "Доступ ограничен",
						subtitle =
							state.message
								?: "Эта ссылка больше не работает. Получите свежую в кабинете — она подключится сама.",
						circle = Red,
						ring = Ring.Alert,
						icon = R.drawable.ic_triangle_alert,
						dot = Red,
						timer = false,
						expired = true,
						card = true,
						clickable = hasSubscription,
						primary = Primary.OpenCabinet,
						secondary = "Повторить",
					)
				else ->
					Presentation(
						title = errorTitle(state.kind),
						subtitle = state.message ?: "",
						circle = Red,
						ring = Ring.Alert,
						icon = R.drawable.ic_triangle_alert,
						dot = Red,
						timer = false,
						expired = false,
						card = true,
						clickable = hasSubscription,
						primary = Primary.None,
						secondary = if (state.kind == TunnelError.NoVpnPermission) "Разрешить" else "Повторить",
					)
			}
	}
}

private fun errorTitle(kind: TunnelError): String =
	when (kind) {
		TunnelError.NoSubscription -> "Подписки пока нет"
		TunnelError.NoVpnPermission -> "Нужно разрешение на VPN"
		TunnelError.SubscriptionUnavailable -> "Не удалось загрузить подписку"
		TunnelError.DeadLink -> "Ссылка устарела"
		TunnelError.Stub -> "Доступ ограничен"
		TunnelError.Core -> "Ядро отвергло конфиг"
		TunnelError.CoreDied -> "Соединение оборвалось"
		TunnelError.Revoked -> "VPN отключила система"
	}

@Composable
private fun Header(title: String, subtitle: String) {
	val palette = LocalPalette.current
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			Icon(painterResource(R.drawable.ic_lock), null, tint = Emerald, modifier = Modifier.size(14.dp))
			Text("ЧАСТНЫЙ ДОСТУП", fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.4.sp, color = Emerald)
		}
		Text(
			title,
			fontSize = 32.sp,
			lineHeight = 37.sp,
			fontWeight = FontWeight.Bold,
			letterSpacing = (-0.64).sp,
			color = palette.onBackground,
		)
		if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 15.sp, lineHeight = 23.sp, color = palette.muted)
	}
}

@Composable
private fun Indicator(presentation: Presentation, since: Long?, onClick: () -> Unit) {
	val palette = LocalPalette.current
	Column(
		modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Box(
			modifier =
				Modifier.size(168.dp)
					.ring(presentation.ring)
					.clip(CircleShape)
					.background(palette.surface)
					.border(1.dp, palette.outline, CircleShape),
			contentAlignment = Alignment.Center,
		) {
			Box(
				modifier =
					Modifier.size(124.dp)
						.clip(CircleShape)
						.background(presentation.circle)
						.clickable(enabled = presentation.clickable, onClick = onClick),
				contentAlignment = Alignment.Center,
			) {
				Icon(painterResource(presentation.icon), null, tint = Color.White, modifier = Modifier.size(48.dp))
			}
		}
		if (presentation.timer && since != null) Elapsed(since)
	}
}

/** HH:MM:SS с момента подключения; тикает раз в секунду. */
@Composable
private fun Elapsed(since: Long) {
	val palette = LocalPalette.current
	var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
	LaunchedEffect(since) {
		while (true) {
			now = System.currentTimeMillis()
			delay(1000)
		}
	}
	val total = ((now - since) / 1000).coerceAtLeast(0)
	val text = "%02d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		Icon(painterResource(R.drawable.ic_clock), null, tint = palette.muted, modifier = Modifier.size(14.dp))
		Text(text, fontSize = 14.sp, color = palette.muted, style = Tabular)
	}
}

@Composable
private fun Modifier.ring(ring: Ring): Modifier {
	val palette = LocalPalette.current
	val animated = animationsEnabled()
	return when (ring) {
		Ring.None -> this
		Ring.Dashed ->
			drawBehind {
				drawCircle(
					color = palette.muted.copy(alpha = 0.5f),
					style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
				)
			}
		Ring.Alert -> drawBehind { drawCircle(color = Red.copy(alpha = 0.5f), style = Stroke(width = 2.dp.toPx())) }
		Ring.Spinner -> {
			val angle =
				if (animated) {
					rememberInfiniteTransition(label = "spin").animateFloat(
						0f,
						360f,
						infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
						label = "angle",
					).value
				} else 0f
			drawBehind {
				val stroke = Stroke(width = 3.dp.toPx())
				drawCircle(color = Amber.copy(alpha = 0.25f), style = stroke)
				drawArc(
					color = Amber,
					startAngle = angle - 90f,
					sweepAngle = 90f,
					useCenter = false,
					style = stroke,
				)
			}
		}
		Ring.Glow -> {
			val pulse =
				if (animated) {
					rememberInfiniteTransition(label = "glow").animateFloat(
						0f,
						1f,
						infiniteRepeatable(tween(1300), RepeatMode.Reverse),
						label = "pulse",
					).value
				} else 0.5f
			drawBehind {
				val spread = 40.dp.toPx() * pulse
				val radius = size.minDimension / 2
				if (spread > 0f) {
					drawCircle(
						brush =
							Brush.radialGradient(
								colorStops =
									arrayOf(
										0f to Emerald.copy(alpha = 0.35f - 0.10f * pulse),
										radius / (radius + spread) to Emerald.copy(alpha = 0.30f - 0.05f * pulse),
										1f to Emerald.copy(alpha = 0f),
									),
								center = Offset(size.width / 2, size.height / 2),
								radius = radius + spread,
							),
						radius = radius + spread,
					)
				}
				drawCircle(color = Emerald, style = Stroke(width = 2.dp.toPx()))
			}
		}
	}
}

/** Выключенные анимации в системе — рисуем статику, как просит спека. */
@Composable
private fun animationsEnabled(): Boolean {
	val context = LocalContext.current
	return remember {
		Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
	}
}

@Composable
private fun PlanCard(info: SubscriptionInfo, dot: Color, expired: Boolean) {
	val palette = LocalPalette.current
	val plan = info.plan
	val user = info.userinfo
	val expiresAt = (plan?.expiresAt ?: user?.expire)?.takeIf { it > 0 }
	val now = System.currentTimeMillis() / 1000
	val isExpired = expired || (expiresAt != null && expiresAt < now)
	val usedBytes = plan?.usedBytes ?: user?.let { (it.upload ?: 0) + (it.download ?: 0) }?.takeIf { user.upload != null || user.download != null }

	Column(
		modifier = Modifier.fillMaxWidth().border(1.dp, palette.outline, RoundedCornerShape(12.dp)).padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
				// Имя своего приложения в карточке не пишем — человек и так в нём.
				Text(plan?.plan ?: "Подписка", fontSize = 14.sp, color = palette.onBackground)
			}
			val label =
				when {
					isExpired -> "истекла"
					expiresAt != null -> "ещё ${daysLeft(expiresAt, now)}"
					else -> ""
				}
			if (label.isNotEmpty()) Text(label, fontSize = 12.sp, color = if (isExpired) Red else palette.muted)
		}
		Column(
			modifier =
				Modifier.fillMaxWidth()
					.drawBehind { drawLine(palette.divider, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
					.padding(top = 12.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			if (plan?.deviceLimit != null) {
				CardRow("Устройства", "${plan.devices ?: "—"} из ${plan.deviceLimit}")
			}
			if (usedBytes != null) CardRow("Трафик", gb(usedBytes))
			if (expiresAt != null) {
				CardRow("Заканчивается", dateOf(expiresAt), valueColor = if (isExpired) Red else null)
			}
		}
	}
}

@Composable
private fun CardRow(label: String, value: String, valueColor: Color? = null) {
	val palette = LocalPalette.current
	Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
		Text(label, fontSize = 12.sp, color = palette.onBackground.copy(alpha = 0.7f))
		Text(value, fontSize = 12.sp, color = valueColor ?: palette.muted, style = Tabular)
	}
}

@Composable
private fun Actions(
	presentation: Presentation,
	update: VersionInfo?,
	onOpenCabinet: () -> Unit,
	onRetry: () -> Unit,
	onInstallUpdate: () -> Unit,
) {
	val palette = LocalPalette.current
	if (presentation.primary == Primary.None && presentation.secondary == null && update == null) return
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		if (presentation.primary == Primary.OpenCabinet) {
			Row(
				modifier =
					Modifier.fillMaxWidth()
						.height(52.dp)
						.clip(RoundedCornerShape(8.dp))
						.background(Emerald)
						.clickable(onClick = onOpenCabinet),
				horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(painterResource(R.drawable.ic_send), null, tint = Color.White, modifier = Modifier.size(16.dp))
				Text("Открыть кабинет", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
			}
		}
		presentation.secondary?.let { label ->
			Row(
				modifier =
					Modifier.fillMaxWidth()
						.clip(RoundedCornerShape(8.dp))
						.clickable(onClick = onRetry)
						.padding(10.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(painterResource(R.drawable.ic_refresh_cw), null, tint = palette.muted, modifier = Modifier.size(14.dp))
				Text(label, color = palette.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
			}
		}
		update?.let { v ->
			Button(onClick = onInstallUpdate, modifier = Modifier.fillMaxWidth()) { Text("Обновить до ${v.versionName}") }
		}
	}
}

private fun daysLeft(expiresAt: Long, now: Long): String {
	val days = ((expiresAt - now) / 86_400).toInt().coerceAtLeast(0)
	val word =
		when {
			days % 10 == 1 && days % 100 != 11 -> "день"
			days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
			else -> "дней"
		}
	return "$days $word"
}

private fun dateOf(unix: Long): String =
	SimpleDateFormat("d MMM yyyy", RU).format(Date(unix * 1000)).replace(".", "")

private fun gb(bytes: Long): String = String.format(RU, "%.1f ГБ", bytes / 1_073_741_824.0)
