package com.amurcanov.tgwsproxy.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CancellationException
import com.amurcanov.tgwsproxy.ProfilesState
import com.amurcanov.tgwsproxy.DEFAULT_PROFILE_ID
import com.amurcanov.tgwsproxy.validSecret
import com.amurcanov.tgwsproxy.validPort
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amurcanov.tgwsproxy.BuildConfig
import com.amurcanov.tgwsproxy.ProxyController
import com.amurcanov.tgwsproxy.ProxyService
import com.amurcanov.tgwsproxy.SettingsStore
import com.amurcanov.tgwsproxy.R
import kotlinx.coroutines.launch

@Composable
fun ConnectionTab(settingsStore: SettingsStore, onProfiles: () -> Unit, appearanceMenu: @Composable () -> Unit) {
    val context = LocalContext.current
    val isRunning by ProxyService.isRunning.collectAsStateWithLifecycle()
    val isVerifiedRunning by ProxyService.isVerifiedRunning.collectAsStateWithLifecycle()
    val serviceStarting by ProxyService.isStarting.collectAsStateWithLifecycle()
    val serviceError by ProxyService.startupError.collectAsStateWithLifecycle()
    val profiles by settingsStore.profiles.collectAsStateWithLifecycle(ProfilesState(DEFAULT_PROFILE_ID, emptyList()))
    val view = LocalView.current

    val isReady by settingsStore.isReady.collectAsStateWithLifecycle(initialValue = false)

    // Settings
    val savedPort by settingsStore.port.collectAsStateWithLifecycle(initialValue = "1443")
    val savedBindIp by settingsStore.bindIp.collectAsStateWithLifecycle(initialValue = "127.0.0.1")
    val savedCfEnabled by settingsStore.cfproxyEnabled.collectAsStateWithLifecycle(initialValue = true)
    val savedPoolSize by settingsStore.poolSize.collectAsStateWithLifecycle(initialValue = 4)
    val savedSecretKey by settingsStore.secretKey.collectAsStateWithLifecycle(initialValue = "LOADING")

    val scope = rememberCoroutineScope()
    val currentVersion = remember { "v${BuildConfig.VERSION_NAME.removePrefix("v")}" }

    if (!isReady || savedSecretKey == "LOADING") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
        return
    }

    var dispatching by remember { mutableStateOf(false) }
    var requestError by rememberSaveable { mutableStateOf(false) }
    val isStarting = dispatching || serviceStarting
    val error = if (requestError) stringResource(R.string.proxy_start_failed) else serviceError
    val statusText = when {
        isVerifiedRunning -> stringResource(R.string.status_connected)
        error != null -> stringResource(R.string.proxy_status_error)
        isStarting || isRunning -> stringResource(R.string.status_connecting)
        else -> stringResource(R.string.status_disconnected)
    }
    val bindIp = savedBindIp.trim()
    val proxyUrl = remember(bindIp, savedPort, savedSecretKey) {
        if (validSecret(savedSecretKey) && validPort(savedPort)) {
            Uri.parse("https://t.me/proxy").buildUpon()
                .appendQueryParameter("server", bindIp)
                .appendQueryParameter("port", savedPort)
                .appendQueryParameter("secret", "dd" + savedSecretKey)
                .build().toString()
        } else null
    }
    var applyMode by rememberSaveable { mutableStateOf("packages") }
    val connectAction: () -> Unit = {
        if (!isRunning && !isStarting) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            requestError = false
            dispatching = true
            scope.launch {
                try {
                    requestError = !ProxyController.startFromSavedSettings(context)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    requestError = true
                } finally {
                    dispatching = false
                }
            }
        }
    }
    val disconnectAction: () -> Unit = {
        if (isRunning) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            ProxyController.stop(context)
        }
    }
    val isActiveVisual = isRunning || isStarting
    val heroInteraction = remember { MutableInteractionSource() }
    val heroPressed by heroInteraction.collectIsPressedAsState()
    val ctaInteraction = remember { MutableInteractionSource() }
    val ctaPressed by ctaInteraction.collectIsPressedAsState()
    val ctaScale by animateFloatAsState(if (ctaPressed) 0.97f else 1f, tween(140), label = "apply_press")
    val logoScale by animateFloatAsState(
        targetValue = if (heroPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "logo_scale"
    )
    val colors = MaterialTheme.colorScheme
    val connectedColor = if (colors.background.luminance() < 0.22f) {
        AppColors.connectedDark
    } else {
        AppColors.onConnected
    }
    val statusColor by animateColorAsState(
        targetValue = when { isVerifiedRunning -> connectedColor; error != null -> colors.error; else -> colors.onSurfaceVariant },
        label = "connection_status_color",
    )
    val ringStrength by animateFloatAsState(
        targetValue = if (isActiveVisual) 1f else 0.4f,
        animationSpec = tween(400),
        label = "connection_glow",
    )
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // Draw-only state. The transition is disposed when the tab loses focus or the proxy stops.
    val wave = if (isVerifiedRunning && lifecycleState == Lifecycle.State.RESUMED) {
        rememberInfiniteTransition(label = "connected_waves").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
            label = "wave_progress",
        )
    } else null
    val actionLabel = stringResource(when {
        isRunning -> R.string.proxy_action_stop
        error != null -> R.string.proxy_retry
        else -> R.string.proxy_action_start
    })

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProxyMark(Modifier.size(46.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.proxy_tagline), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            appearanceMenu()
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(178.dp).drawWithCache {
                    val glow = Brush.radialGradient(
                        colors = listOf(colors.primary.copy(alpha = 0.32f), Color.Transparent),
                        radius = size.minDimension / 2f,
                    )
                    val waveRadius = 69.dp.toPx()
                    val waveRange = 18.dp.toPx()
                    val waveStroke = Stroke(1.5.dp.toPx())
                    onDrawBehind {
                        drawCircle(glow, alpha = ringStrength)
                        drawCircle(colors.primary.copy(alpha = 0.09f * ringStrength), radius = size.minDimension * 0.45f)
                        drawCircle(colors.primary.copy(alpha = 0.12f * ringStrength), radius = size.minDimension * 0.405f)
                        wave?.value?.let { progress ->
                            repeat(2) { index ->
                                val phase = (progress + index * 0.5f) % 1f
                                drawCircle(
                                    color = colors.primary.copy(alpha = 0.32f * (1f - phase) * (1f - phase)),
                                    radius = waveRadius + waveRange * phase,
                                    style = waveStroke,
                                )
                            }
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = if (isRunning) disconnectAction else connectAction,
                    enabled = !isStarting,
                    interactionSource = heroInteraction,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(136.dp).scale(logoScale)
                        .semantics { contentDescription = actionLabel },
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_telegram_logo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Crossfade(targetState = statusText, animationSpec = tween(190), label = "proxy_status") { status ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Box(Modifier.size(9.dp).background(statusColor, CircleShape))
                    Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            ObservedRoute()
            if (error != null) Text(error, color = colors.error, textAlign = TextAlign.Center)
            if (isStarting) LinearProgressIndicator(Modifier.width(80.dp).padding(top = 8.dp))
            TextButton(
                onClick = if (isRunning) disconnectAction else connectAction,
                enabled = !isStarting,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }

        Surface(
            onClick = {
                if (proxyUrl != null) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (applyMode == "packages") applyToTelegramPackages(context, proxyUrl)
                    else openTelegram(context, proxyUrl)
                }
            },
            enabled = isRunning && proxyUrl != null,
            interactionSource = ctaInteraction,
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = if (isRunning) Color.White else colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().scale(ctaScale),
        ) {
            val fill = if (isRunning) {
                Brush.verticalGradient(0f to Color(0xFF2478F5), 0.45f to Color(0xFF1768CB), 1f to Color(0xFF1152B0))
            } else {
                Brush.verticalGradient(listOf(colors.surfaceVariant, colors.surfaceVariant))
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .background(fill).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
                Text(
                    stringResource(R.string.apply_in_telegram),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
            }
        }

        ProxyStatusPanel(savedCfEnabled, savedPoolSize, savedPort, currentVersion)

        TrafficPanel()

        Surface(onClick = onProfiles, shape = RoundedCornerShape(20.dp), color = colors.surface) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Layers, null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text(profiles.active?.name ?: stringResource(R.string.profile_default), style = MaterialTheme.typography.titleMedium)
                    Text(bindIp + ":" + savedPort, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip(stringResource(R.string.profiles), applyMode == "packages", modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                applyMode = "packages"
                if (proxyUrl != null && isRunning) applyToTelegramPackages(context, proxyUrl)
            }
            ModeChip(stringResource(R.string.apply_mode_link), applyMode == "link", modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                applyMode = "link"
                if (proxyUrl != null) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Proxy", proxyUrl)
                    if (android.os.Build.VERSION.SDK_INT >= 24) clip.description.extras = android.os.PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
internal fun ObservedRoute() {
    val running by ProxyService.isRunning.collectAsStateWithLifecycle()
    val stats by ProxyService.traffic.collectAsStateWithLifecycle()
    val routes = buildList {
        if ((stats?.websocket ?: 0L) > 0) add("WS")
        if ((stats?.cloudflare ?: 0L) > 0) add("Cloudflare")
        if ((stats?.tcp ?: 0L) > 0) add("TCP")
    }
    if (running) {
        Text(
            if (routes.isEmpty()) stringResource(R.string.route_waiting)
            else stringResource(R.string.routes_used, routes.joinToString(" · ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrafficPanel() {
    val stats by ProxyService.traffic.collectAsStateWithLifecycle()
    AppSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.traffic_down), style = MaterialTheme.typography.bodySmall)
                Text(stats?.downloaded ?: "—", style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.traffic_up), style = MaterialTheme.typography.bodySmall)
                Text(stats?.uploaded ?: "—", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
internal fun ProxyMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.proxy_brand),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(13.dp)),
    )
}

@Composable
private fun ProxyStatusPanel(cfEnabled: Boolean, poolSize: Int, port: String, version: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProxyStatusItem(Icons.Default.Public, if (cfEnabled) "CF" else stringResource(R.string.direct_mode), Modifier.weight(1f))
            ProxyStatusItem(Icons.Default.Layers, stringResource(R.string.pool_short, poolSize), Modifier.weight(1f))
            ProxyStatusItem(Icons.Default.SignalCellularAlt, stringResource(R.string.port_short, port), Modifier.weight(1f))
            ProxyStatusItem(Icons.Default.Info, version, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProxyStatusItem(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private val telegramPackages = listOf(
    "org.telegram.messenger",
    "com.radolyn.ayugram",
    "com.exteragram.messenger",
    "org.telegram.plus",
    "ir.ilmili.telegraph",
    "org.telegram.BifToGram",
    "tw.nekomimi.nekogram",
    "xyz.nextalone.nagram",
    "uz.unnarsx.cherrygram",
    "org.telegram.mdgram",
    "org.forkclient.messenger.beta",
    "app.nicegram",
    "top.qwq2333.nullgram",
    "com.iMe.android",
    "ru.dahl.messenger",
    "com.scriptsaz.litegram",
    "org.thunderdog.challegram"
)

private fun applyToTelegramPackages(context: Context, url: String) {
    val pm = context.packageManager
    val availablePackages = telegramPackages.filter {
        try {
            pm.getPackageInfo(it, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    if (availablePackages.isEmpty()) {
        Toast.makeText(context, "Клиенты не найдены", Toast.LENGTH_SHORT).show()
        return
    }

    val targetedIntents = availablePackages.map { pkg ->
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(pkg)
        }
    }

    if (targetedIntents.size == 1) {
        val intent = targetedIntents.first().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка при открытии клиента", Toast.LENGTH_SHORT).show()
        }
    } else {
        val chooserIntent = Intent.createChooser(targetedIntents.first(), "Выберите клиент")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedIntents.drop(1).toTypedArray())
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка при выборе клиента", Toast.LENGTH_SHORT).show()
        }
    }
}
