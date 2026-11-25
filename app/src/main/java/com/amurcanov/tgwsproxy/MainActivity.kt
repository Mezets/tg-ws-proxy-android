package com.amurcanov.tgwsproxy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amurcanov.tgwsproxy.ui.AppUpdateDialog
import com.amurcanov.tgwsproxy.ui.ConnectionTab
import com.amurcanov.tgwsproxy.ui.AppearanceMenu
import com.amurcanov.tgwsproxy.ui.InfoTab
import com.amurcanov.tgwsproxy.ui.LogsTab
import com.amurcanov.tgwsproxy.ui.SettingsTab
import com.amurcanov.tgwsproxy.ui.ProfilesTab
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.amurcanov.tgwsproxy.ui.TgWsProxyTheme
import com.amurcanov.tgwsproxy.ui.openUrlInBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lang_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "ru") ?: "ru"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val context = LocalContext.current
            val settingsStore = remember { SettingsStore(context) }
            val themeMode by settingsStore.themeMode
                .collectAsStateWithLifecycle(initialValue = "dark")
            val isDynamicColor by settingsStore.isDynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            val themePalette by settingsStore.themePalette
                .collectAsStateWithLifecycle(initialValue = "indigo")
            val scope = rememberCoroutineScope()

            LaunchedEffect(settingsStore) {
                settingsStore.migrateLegacyDefaults()
            }

            TgWsProxyTheme(themeMode = themeMode, dynamicColor = isDynamicColor, themePalette = themePalette) {
                Box(Modifier.fillMaxSize()) {
                    AppBackdrop(Modifier.matchParentSize())
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        MainContent(settingsStore) {
                            AppearanceMenu(
                                currentTheme = themeMode,
                                onThemeChange = { mode -> scope.launch { settingsStore.saveThemeMode(mode) } },
                                isDynamicColor = isDynamicColor,
                                onDynamicColorChange = { enabled -> scope.launch { settingsStore.saveDynamicColor(enabled) } },
                                currentPalette = themePalette,
                                onPaletteChange = { palette -> scope.launch { settingsStore.saveThemePalette(palette) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class NavItem(
    val label: String,
    val iconRes: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(settingsStore: SettingsStore, appearanceMenu: @Composable () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val updatePostponeUntil by settingsStore.updatePostponeUntil.collectAsStateWithLifecycle(initialValue = 0L)
    val updatePostponeVersion by settingsStore.updatePostponeVersion.collectAsStateWithLifecycle(initialValue = "")
    val updateCheckIntervalHours by settingsStore.updateCheckIntervalHours.collectAsStateWithLifecycle(
        initialValue = UPDATE_CHECK_NEVER
    )
    val updateLastCheckAt by settingsStore.updateLastCheckAt.collectAsStateWithLifecycle(initialValue = 0L)
    var pendingRelease by remember { mutableStateOf<AppReleaseInfo?>(null) }
    val currentVersion = remember { "v${BuildConfig.VERSION_NAME.removePrefix("v")}" }
    val currentUpdatePostponeUntil by rememberUpdatedState(updatePostponeUntil)
    val currentUpdatePostponeVersion by rememberUpdatedState(updatePostponeVersion)
    val navItems = listOf(
        NavItem(stringResource(R.string.nav_proxy), Icons.Default.PowerSettingsNew),
        NavItem(stringResource(R.string.settings), Icons.Default.Settings),
        NavItem(stringResource(R.string.nav_logs), Icons.Default.Terminal),
        NavItem(stringResource(R.string.profiles), Icons.Default.Layers),
        NavItem(stringResource(R.string.nav_info_short), Icons.Default.Info)
    )
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val tabs = rememberSaveableStateHolder()

    DisposableEffect(Unit) {
        LogManager.startListening()
        onDispose { LogManager.stopListening() }
    }

    LaunchedEffect(updateCheckIntervalHours, updateLastCheckAt) {
        if (updateCheckIntervalHours == UPDATE_CHECK_NEVER) return@LaunchedEffect

        val intervalMillis = updateIntervalHoursToMillis(updateCheckIntervalHours)
            ?: updateIntervalHoursToMillis(DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)
            ?: 12L * 60L * 60L * 1000L

        if (updateLastCheckAt > 0L) {
            val nextCheckAt = updateLastCheckAt + intervalMillis
            val now = System.currentTimeMillis()
            if (nextCheckAt > now) {
                delay(nextCheckAt - now)
            }
        }

        if (!isActive) return@LaunchedEffect

        val checkedAt = System.currentTimeMillis()
        val release = fetchLatestReleaseInfo(currentVersion)
        settingsStore.saveUpdateState(
            lastCheckAt = checkedAt,
            latestVersion = release?.versionTag ?: "",
            error = if (release == null) context.getString(R.string.update_check_failed_short) else ""
        )

        if (release == null) {
            Log.w("TgWsProxy", "[WARN] Update check: no release info, local=$currentVersion")
        } else {
            val hasUpdate = isNewerVersion(currentVersion, release.versionTag)
            val isPostponed =
                currentUpdatePostponeVersion == release.versionTag && checkedAt < currentUpdatePostponeUntil
            Log.i(
                "TgWsProxy",
                "Update check: local=$currentVersion remote=${release.versionTag} newer=$hasUpdate postponed=$isPostponed"
            )

            if (hasUpdate && !isPostponed) {
                settingsStore.saveUpdateDialogShown(release.versionTag, checkedAt)
                pendingRelease = release
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        containerColor = Color.Transparent,
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
            .pointerInput(selectedTab, keyboardVisible) {
                if (keyboardVisible) return@pointerInput

                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        dragTargetIndex = -1
                        dragProgress = 0f
                    },
                    onDragCancel = {
                        dragTargetIndex = -1
                        dragProgress = 0f
                    },
                    onDragEnd = {
                        if (dragTargetIndex in navItems.indices && dragProgress >= 0.5f) {
                            selectedTab = dragTargetIndex
                        }
                        dragTargetIndex = -1
                        dragProgress = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                    if (abs(totalDrag) < 12f) {
                        dragTargetIndex = -1
                        dragProgress = 0f
                        return@detectHorizontalDragGestures
                    }

                    val candidate = if (totalDrag < 0f) selectedTab + 1 else selectedTab - 1
                    if (candidate !in navItems.indices) {
                        dragTargetIndex = -1
                        dragProgress = 0f
                        return@detectHorizontalDragGestures
                    }

                    dragTargetIndex = candidate
                    dragProgress = (abs(totalDrag) / 180f).coerceIn(0f, 1f)
                }
            }
        ) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (wide && !keyboardVisible) {
                    NavigationRail(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        navItems.forEachIndexed { index, item ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(item.iconRes, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(190, delayMillis = 45)) +
                            slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 16 * direction })
                            .togetherWith(fadeOut(tween(135)))
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .then(if (wide && !keyboardVisible) Modifier.navigationBarsPadding() else Modifier),
                    label = "tab_content"
                ) { page ->
                    tabs.SaveableStateProvider(page) {
                        when (page) {
                            0 -> ConnectionTab(settingsStore, onProfiles = { selectedTab = 3 }, appearanceMenu = appearanceMenu)
                            1 -> {
                                val profileState by settingsStore.profiles.collectAsStateWithLifecycle(ProfilesState(DEFAULT_PROFILE_ID, emptyList()))
                                key(profileState.activeId) { SettingsTab(settingsStore, profileState.activeId, appearanceMenu) }
                            }
                            2 -> LogsTab(settingsStore)
                            3 -> ProfilesTab(settingsStore)
                            4 -> InfoTab(settingsStore, appearanceMenu)
                        }
                    }
                }
            }

            if (!keyboardVisible && !wide) {
                ProxyNavigationBar(
                    navItems = navItems,
                    selectedTab = selectedTab,
                    dragTargetIndex = dragTargetIndex,
                    dragProgress = dragProgress,
                    onTabSelected = { index ->
                        selectedTab = index
                        dragTargetIndex = -1
                        dragProgress = 0f
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    pendingRelease?.let { release ->
        AppUpdateDialog(
            release = release,
            onPostpone = {
                pendingRelease = null
                Toast.makeText(context, context.getString(R.string.update_postponed_24h), Toast.LENGTH_SHORT).show()
                scope.launch {
                    val now = System.currentTimeMillis()
                    settingsStore.saveUpdatePostpone(
                        version = release.versionTag,
                        until = now + 24L * 60L * 60L * 1000L
                    )
                    settingsStore.saveUpdateDialogAction(
                        version = release.versionTag,
                        action = UPDATE_DIALOG_ACTION_POSTPONED,
                        actedAt = now
                    )
                }
            },
            onUpdate = {
                pendingRelease = null
                scope.launch {
                    settingsStore.saveUpdateDialogAction(
                        version = release.versionTag,
                        action = UPDATE_DIALOG_ACTION_UPDATE,
                        actedAt = System.currentTimeMillis()
                    )
                    openUrlInBrowser(context, release.releaseUrl)
                }
            }
        )
    }
}

@Composable
private fun ProxyNavigationBar(
    navItems: List<NavItem>,
    selectedTab: Int,
    dragTargetIndex: Int,
    dragProgress: Float,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.22f
    val selectedColor = colors.onPrimaryContainer
    val unselectedColor = colors.onSurfaceVariant.copy(alpha = 0.85f)
    val shellColor = if (isDark) {
        colors.surface.copy(alpha = 0.94f)
    } else {
        lerp(colors.surface, colors.surfaceVariant, 0.48f).copy(alpha = 0.95f)
    }
    val shellBorder = if (isDark) {
        colors.outlineVariant.copy(alpha = 0.24f)
    } else {
        colors.outline.copy(alpha = 0.16f)
    }
    val indicatorColor = if (isDark) {
        colors.primaryContainer
    } else {
        lerp(colors.primaryContainer, colors.surface, 0.18f).copy(alpha = 0.97f)
    }
    val indicatorIndex = remember { Animatable(selectedTab.toFloat()) }
    val dragVisualIndex = indicatorIndex.value

    LaunchedEffect(selectedTab, dragTargetIndex, dragProgress) {
        if (dragTargetIndex in navItems.indices) {
            val target = selectedTab.toFloat() + (dragTargetIndex - selectedTab) * dragProgress
            indicatorIndex.snapTo(target)
        } else {
            indicatorIndex.animateTo(
                targetValue = selectedTab.toFloat(),
                animationSpec = tween(
                    durationMillis = 220,
                    easing = CubicBezierEasing(0.2f, 0.9f, 0.24f, 1f)
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val trackPadding = 5.dp
        val itemWidth = (maxWidth - trackPadding * 2) / navItems.size
        val indicatorOffset = trackPadding + itemWidth * dragVisualIndex
        val indicatorEnd = (maxWidth - indicatorOffset - itemWidth).coerceAtLeast(0.dp)
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        val labelLimit = with(LocalDensity.current) { (itemWidth - 4.dp).roundToPx() }
        val showAllLabels = navItems.all {
            textMeasurer.measure(AnnotatedString(it.label), style = labelStyle, softWrap = false).size.width <= labelLimit
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = shellColor,
            border = BorderStroke(1.dp, shellBorder),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = indicatorColor,
                        modifier = Modifier
                            .matchParentSize()
                            .padding(start = indicatorOffset, end = indicatorEnd, top = 4.dp, bottom = 4.dp)
                    ) {}
    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = trackPadding, vertical = 4.dp)
                            .selectableGroup()
                    ) {
                        navItems.forEachIndexed { index, item ->
                            val emphasis = (1f - abs(index - dragVisualIndex)).coerceIn(0f, 1f)
                            val iconColor = lerp(unselectedColor, selectedColor, emphasis)
    
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .selectable(selected = selectedTab == index, role = Role.Tab) { onTabSelected(index) },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = item.iconRes,
                                    contentDescription = if (showAllLabels) null else item.label,
                                    modifier = Modifier.size(22.dp),
                                    tint = iconColor
                                )
                                if (showAllLabels) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (emphasis > 0.55f) FontWeight.SemiBold else FontWeight.Medium,
                                    color = iconColor,
                                    textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 1.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (!showAllLabels) {
                    Text(
                        navItems[selectedTab].label,
                        style = labelStyle,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.22f
    Box(modifier.drawWithCache {
        val glow = Brush.radialGradient(
            colors = listOf(colors.primary.copy(alpha = if (dark) 0.14f else 0.08f), Color.Transparent),
            center = Offset(size.width * 0.16f, size.height * 0.06f),
            radius = size.width.coerceAtLeast(1f),
        )
        onDrawBehind {
            drawRect(colors.background)
            drawRect(glow)
        }
    })
}

/**
 * Optimized LogManager: uses a Channel + batching approach to avoid
 * creating a new list on every single log line — reduces GC pressure
 * and eliminates UI jank caused by high-frequency log updates.
 *
 * Key optimizations:
 * - Channel-based buffering: log lines are queued, not applied immediately
 * - Batch processing: up to 20 lines applied per tick (every 150ms)
 * - Array-backed list with cap of 50: avoids growing/shrinking allocations
 * - Duplicate merging: last-entry count increment done in-place conceptually
 */
object LogManager {
    val logs = MutableStateFlow<List<LogEntry>>(emptyList())
    private var job: Job? = null
    private var logcatProcess: Process? = null
    private val nextKey = AtomicLong(0)

    // Buffered channel — absorbs bursts of log lines without blocking the reader
    private val logChannel = Channel<LogEntry>(capacity = BUFFERED)

    fun startListening() {
        if (job?.isActive == true) return
        job = CoroutineScope(Dispatchers.IO).launch {
            // Start logcat reader coroutine
            val readerJob = launch(Dispatchers.IO) {
                try {
                    val pid = android.os.Process.myPid()
                    val process = ProcessBuilder("logcat", "-v", "tag", "--pid", pid.toString())
                        .redirectErrorStream(true)
                        .start()
                        
                    logcatProcess = process
                    
                    process.inputStream.bufferedReader().use { reader ->
                        while (isActive) {
                            val line = try { reader.readLine() } catch (e: Exception) { null } ?: break
                            val entry = parseLine(line) ?: continue
                            logChannel.trySend(entry)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    logcatProcess?.destroy()
                    logcatProcess = null
                }
            }

            // Batch consumer: collects queued entries and applies in batches
            launch {
                val pendingBatch = mutableListOf<LogEntry>()
                while (isActive) {
                    // Drain the channel (non-blocking)
                    var received = logChannel.tryReceive()
                    while (received.isSuccess) {
                        pendingBatch.add(received.getOrThrow())
                        if (pendingBatch.size >= 20) break // cap batch size
                        received = logChannel.tryReceive()
                    }

                    if (pendingBatch.isNotEmpty()) {
                        // Apply batch to state — single list mutation
                        logs.value = applyBatch(logs.value, pendingBatch)
                        pendingBatch.clear()
                    }

                    // Throttle updates — 150ms between UI refreshes
                    delay(150)
                }
            }

            readerJob.join()
        }
    }

    /**
     * Efficiently applies a batch of new entries to the current log list.
     * Merges consecutive duplicates and caps at 50 entries.
     */
    private fun applyBatch(current: List<LogEntry>, batch: List<LogEntry>): List<LogEntry> {
        val result = ArrayDeque(current)
        for (entry in batch) {
            var merged = false
            val searchDepth = minOf(result.size, 10)
            for (i in result.indices.reversed().take(searchDepth)) {
                if (result[i].message == entry.message) {
                    val existing = result.removeAt(i)
                    result.addLast(existing.copy(count = existing.count + 1))
                    merged = true
                    break
                }
            }
            if (!merged) {
                result.addLast(entry)
            }
        }
        while (result.size > 50) {
            result.removeFirst()
        }
        return result.toList()
    }

    fun stopListening() {
        job?.cancel()
        job = null
        logcatProcess?.destroy()
        logcatProcess = null
    }

    fun clearLogs() {
        logs.value = emptyList()
    }

    private fun parseLine(raw: String): LogEntry? {
        var message: String
        val isError: Boolean
        val priority: Int

        when {
            raw.contains("[ERROR]") -> {
                message = raw.substringAfter("[ERROR]").trim()
                isError = true
                priority = 6 // Log.ERROR
            }
            raw.contains("[WARN]") -> {
                message = raw.substringAfter("[WARN]").trim()
                isError = false // WARN is not ERROR, but distinctive
                priority = 5 // Log.WARN
            }
            raw.contains("[DEBUG]") -> {
                message = raw.substringAfter("[DEBUG]").trim()
                isError = false
                priority = 3 // Log.DEBUG
            }
            raw.contains("TgWsProxy") -> {
                // Info doesn't have a prefix, so we strip basically everything up to the actual message
                var msg = raw.substringAfter("TgWsProxy:").trim()
                if (msg.startsWith("[ERROR]") || msg.startsWith("[WARN]") || msg.startsWith("[DEBUG]")) {
                     return null // Handled above, but just in case
                }

                // Strip dynamic metrics like ↑3.3KB ↓1.1KB 0.3с so that lines can collapse
                if (msg.contains("↑")) {
                    msg = msg.substringBefore("↑").trim()
                }
                if (msg.contains("↓")) {
                    msg = msg.substringBefore("↓").trim()
                }

                message = msg
                isError = false
                priority = 4 // Log.INFO
            }
            else -> return null
        }

        // Remove emojis and stickers
        val emojiRegex = Regex("[\\x{1F300}-\\x{1F5FF}\\x{1F900}-\\x{1F9FF}\\x{1F600}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}\\x{1F191}-\\x{1F251}\\x{1F004}\\x{1F0CF}\\x{1F170}-\\x{1F171}\\x{1F17E}-\\x{1F17F}\\x{1F18E}\\x{3030}\\x{2B50}\\x{2B55}\\x{2934}-\\x{2935}\\x{2B05}-\\x{2B07}\\x{2B1B}-\\x{2B1C}\\x{3297}\\x{3299}\\x{303D}\\x{00A9}\\x{00AE}\\x{2122}\\x{23F3}\\x{24C2}\\x{23E9}-\\x{23EF}\\x{25B6}\\x{23F8}-\\x{23FA}⚠✅❌⚡🔥🔄🔗]")
        message = LogPrivacy.forDisplay(message.replace(emojiRegex, "").trim())

        val isEssential = listOf(
            "pool", "key:", "started", "address:", "error", "failed", "blocked",
            "Пул", "Ключ:", "запущен", "Адрес:", "ошибка", "провалены", "заблокирован"
        ).any { marker -> message.contains(marker, ignoreCase = true) }

        return LogEntry(
            key = "log_${nextKey.getAndIncrement()}",
            message = message,
            count = 1,
            isError = isError,
            priority = priority,
            isEssential = isEssential
        )
    }}
