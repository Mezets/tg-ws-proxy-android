package com.amurcanov.tgwsproxy.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import com.amurcanov.tgwsproxy.LogPrivacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amurcanov.tgwsproxy.LogEntry
import com.amurcanov.tgwsproxy.LogManager
import com.amurcanov.tgwsproxy.R
import com.amurcanov.tgwsproxy.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class LogFilter(val priority: Int?) { ALL(null), INFO(4), WARN(5), ERROR(6) }

@Composable
fun LogsTab(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLogs by LogManager.logs.collectAsStateWithLifecycle()
    val hidden by settingsStore.logShowNull.collectAsStateWithLifecycle(initialValue = false)
    val savedDebug by settingsStore.logShowDebug.collectAsStateWithLifecycle(initialValue = false)
    val savedInfo by settingsStore.logShowInfo.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_LOG_SHOW_INFO)
    val savedError by settingsStore.logShowError.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_LOG_SHOW_ERROR)
    val autoScroll by settingsStore.logAutoscroll.collectAsStateWithLifecycle(initialValue = true)
    val feedback = remember { SnackbarHostState() }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var showMenu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val stackLog = LocalDensity.current.fontScale >= 1.35f && LocalConfiguration.current.screenWidthDp < 600
    val filteredLogs = remember(currentLogs, filter, hidden) {
        when {
            hidden -> emptyList()
            filter == LogFilter.ALL -> currentLogs
            else -> currentLogs.filter { it.priority == filter.priority }
        }
    }

    val exportLog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                    output.bufferedWriter(Charsets.UTF_8).use { writer ->
                        currentLogs.forEach { writer.appendLine(transferLine(it)) }
                    }
                }
                feedback.showSnackbar(context.getString(R.string.logs_exported))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                feedback.showSnackbar(context.getString(R.string.profile_operation_error))
            }
        }
    }
    fun copy(entries: List<LogEntry>) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TG WS Proxy logs", entries.joinToString("\n", transform = ::transferLine)))
        scope.launch { feedback.showSnackbar(context.getString(R.string.copied)) }
    }

    val listState = rememberLazyListState()
    var hasInitialScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(filter, filteredLogs.lastOrNull()?.key, autoScroll) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            if (hasInitialScrolled) listState.animateScrollToItem(filteredLogs.lastIndex)
            else {
                listState.scrollToItem(filteredLogs.lastIndex)
                hasInitialScrolled = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.nav_logs),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { LogManager.clearLogs() }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.clear), tint = colors.primary)
                }
                IconButton(
                    enabled = currentLogs.isNotEmpty(),
                    onClick = { copy(currentLogs) },
                ) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.copy))
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Tune, stringResource(R.string.log_options), tint = colors.primary)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (autoScroll) R.string.autoscroll_disable else R.string.autoscroll_enable)) },
                            onClick = { showMenu = false; scope.launch { settingsStore.saveLogAutoscroll(!autoScroll) } },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.logs_export)) },
                            enabled = currentLogs.isNotEmpty(),
                            onClick = { showMenu = false; exportLog.launch("tg-ws-proxy-logs.txt") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (hidden) R.string.show_logs else R.string.hide_logs)) },
                            onClick = {
                                showMenu = false
                                scope.launch { settingsStore.saveLogFilters(savedDebug, savedInfo, savedError, !hidden) }
                            },
                        )
                    }
                }
            }

            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LogFilter.entries.chunked(if (stackLog) 2 else 4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            val selected = filter == option
                            Box(
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                                    .selectable(selected = selected, enabled = !hidden, role = Role.Tab) { filter = option },
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    color = if (selected && !hidden) colors.primary else colors.surfaceVariant,
                                    contentColor = if (selected && !hidden) colors.onPrimary else colors.onSurfaceVariant,
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            if (option == LogFilter.ALL) stringResource(R.string.logs_all) else option.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = colors.surface.copy(alpha = 0.56f),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (hidden) R.string.logs_hidden else R.string.logs_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filteredLogs, key = { it.key }) { entry -> LogLine(entry, stackLog, onCopy = { copy(listOf(entry)) }) }
                    }
                }
            }
        }
        SnackbarHost(feedback, Modifier.align(Alignment.BottomCenter))
    }
}

private fun logLevel(priority: Int): String = when {
    priority >= 6 -> "ERROR"
    priority == 5 -> "WARN"
    priority == 4 -> "INFO"
    else -> "DEBUG"
}

private fun transferLine(entry: LogEntry): String {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMillis))
    return "$time ${logLevel(entry.priority)} ${LogPrivacy.forTransfer(entry.message)} ×${entry.count}"
}

@Composable
private fun LogLine(entry: LogEntry, stacked: Boolean, onCopy: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.22f
    val level = logLevel(entry.priority)
    val successful = entry.priority == 4 && (entry.message.contains("Proxy ready", true) || entry.message.contains("session established", true))
    val accent = when {
        entry.priority >= 6 -> colors.error
        entry.priority == 5 -> if (dark) Color(0xFFFFB547) else Color(0xFF805100)
        successful -> if (dark) Color(0xFF32D583) else Color(0xFF17643E)
        entry.priority == 4 -> colors.primary
        else -> colors.onSurfaceVariant
    }
    val time = remember(entry.timestampMillis) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMillis))
    }
    val lineModifier = Modifier.fillMaxWidth()
        .clickable(onClickLabel = stringResource(R.string.copy), onClick = onCopy)
        .heightIn(min = 48.dp).padding(vertical = 6.dp)
    val metadata: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Surface(shape = RoundedCornerShape(5.dp), color = accent.copy(alpha = 0.12f), contentColor = accent) {
                Text(level, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
    val message: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            text = if (entry.count > 1) entry.message + " ×" + entry.count else entry.message,
            color = if (entry.priority >= 5 || successful) accent else colors.onSurface,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = modifier,
        )
    }
    if (stacked) {
        Column(lineModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            metadata()
            message(Modifier.fillMaxWidth())
        }
    } else {
        Row(lineModifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
            metadata()
            message(Modifier.weight(1f))
        }
    }
}
