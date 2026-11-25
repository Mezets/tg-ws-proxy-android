package com.amurcanov.tgwsproxy.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amurcanov.tgwsproxy.*
import com.amurcanov.tgwsproxy.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesTab(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by store.profiles.collectAsStateWithLifecycle(ProfilesState(DEFAULT_PROFILE_ID, emptyList()))
    val running by ProxyService.isRunning.collectAsStateWithLifecycle()
    val starting by ProxyService.isStarting.collectAsStateWithLifecycle()
    val locked = running || starting
    val snackbar = remember { SnackbarHostState() }
    var menuProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportSecret by rememberSaveable { mutableStateOf(false) }
    var documentId by rememberSaveable { mutableStateOf<String?>(null) }
    var documentSecret by rememberSaveable { mutableStateOf(false) }
    var importLink by rememberSaveable { mutableStateOf(false) }
    var link by rememberSaveable { mutableStateOf("") }
    var invalidLink by rememberSaveable { mutableStateOf(false) }
    fun perform(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                snackbar.showSnackbar(context.getString(R.string.profile_operation_error))
            }
        }
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform {
            val profile = withContext(Dispatchers.IO) {
                val input = requireNotNull(context.contentResolver.openInputStream(uri))
                input.use {
                    val buffer = ByteArray(MAX_PROFILE_BYTES + 1)
                    var total = 0
                    while (total < buffer.size) {
                        val count = it.read(buffer, total, buffer.size - total)
                        if (count < 0) break
                        total += count
                    }
                    require(total <= MAX_PROFILE_BYTES)
                    decodePortableProfile(String(buffer, 0, total, Charsets.UTF_8))
                }
            }
            store.saveProfile(profile)
            snackbar.showSnackbar(context.getString(R.string.profile_imported))
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val profile = state.entries.firstOrNull { it.id == documentId }
        val includeSecret = documentSecret
        documentId = null
        documentSecret = false
        if (uri != null && profile != null) perform {
            withContext(Dispatchers.IO) {
                val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                output.use { it.write(encodePortableProfile(profile, includeSecret).toByteArray(Charsets.UTF_8)) }
            }
            snackbar.showSnackbar(context.getString(R.string.profile_exported))
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profiles), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { editing = "" }) {
                        Icon(Icons.Default.Add, stringResource(R.string.profile_add))
                    }
                }
            }
            items(state.entries, key = { it.id }) { profile ->
                val active = profile.id == state.activeId
                val colors = MaterialTheme.colorScheme
                val border by animateColorAsState(
                    if (active) colors.primary else colors.outlineVariant,
                    tween(210), label = "active_profile",
                )
                Surface(
                    color = if (active) colors.surfaceVariant else colors.surface,
                    contentColor = colors.onSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, border),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            if (active) Icons.Default.CheckCircle else Icons.Default.Layers,
                            if (active) stringResource(R.string.profile_active) else null,
                            tint = colors.primary,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(profile.host + ":" + profile.port, style = MaterialTheme.typography.bodyMedium)
                            val route = if (profile.cloudflare) "Cloudflare CDN" else stringResource(R.string.direct_mode)
                            Text(route + " · " + stringResource(R.string.pool_short, profile.poolSize),
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        IconButton(onClick = { menuProfileId = profile.id }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.profile_actions), tint = colors.primary)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.profile_import_file)) }
                TextButton(onClick = { importLink = true; invalidLink = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.profile_import_link)) }
                if (locked) Text(stringResource(R.string.profile_stop_to_switch), style = MaterialTheme.typography.bodySmall)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val menuProfile = state.entries.firstOrNull { it.id == menuProfileId }
    if (menuProfile != null) {
        val active = menuProfile.id == state.activeId
        ModalBottomSheet(
            onDismissRequest = { menuProfileId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(menuProfile.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
                if (active) Text(
                    stringResource(R.string.profile_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                if (!active) ProfileMenuAction(Icons.Default.CheckCircle, R.string.profile_activate, enabled = !locked) {
                    menuProfileId = null
                    perform { store.activateProfile(menuProfile.id) }
                }
                ProfileMenuAction(Icons.Default.Edit, R.string.profile_edit, enabled = !active || !locked) {
                    menuProfileId = null
                    editing = menuProfile.id
                }
                ProfileMenuAction(Icons.Default.Share, R.string.profile_export) {
                    menuProfileId = null
                    exportId = menuProfile.id
                    exportSecret = false
                }
                ProfileMenuAction(Icons.Default.DeleteOutline, R.string.profile_delete,
                    enabled = state.entries.size > 1 && (!active || !locked), destructive = true) {
                    menuProfileId = null
                    deleting = menuProfile.id
                }
            }
        }
    }

    val editId = editing
    if (editId != null) {
        val current = state.entries.firstOrNull { it.id == editId }
        if (editId.isEmpty() || current != null) {
            val draft = remember(editId, current?.id) { current ?: ProxyProfile() }
            ProfileEditor(draft, editId.isNotEmpty(), onDismiss = { editing = null }) { updated ->
                if (updated.id != state.activeId || !locked) {
                    editing = null
                    perform { store.saveProfile(updated) }
                }
            }
        }
    }

    val toDelete = state.entries.firstOrNull { it.id == deleting }
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.profile_delete)) },
            text = { Text(stringResource(R.string.profile_confirm_delete, toDelete.name)) },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel_action)) } },
            confirmButton = {
                TextButton(enabled = toDelete.id != state.activeId || !locked, onClick = {
                    deleting = null
                    perform { store.deleteProfile(toDelete.id) }
                }) { Text(stringResource(R.string.profile_delete)) }
            },
        )
    }

    val exporting = state.entries.firstOrNull { it.id == exportId }
    if (exporting != null) {
        AlertDialog(
            onDismissRequest = { exportId = null },
            title = { Text(stringResource(R.string.profile_export)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_export_hint))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.profile_include_secret), Modifier.weight(1f))
                        Switch(exportSecret, { exportSecret = it }, enabled = exporting.secret.isNotBlank())
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val url = profileShareLink(encodePortableProfile(exporting, exportSecret))
                    exportId = null
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }, null))
                }) { Text(stringResource(R.string.share_action)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    documentId = exporting.id
                    documentSecret = exportSecret
                    exportId = null
                    exportFile.launch("tg-ws-profile.json")
                }) { Text(stringResource(R.string.save_action)) }
            },
        )
    }

    if (importLink) {
        AlertDialog(
            onDismissRequest = { importLink = false },
            title = { Text(stringResource(R.string.profile_import_link)) },
            text = {
                OutlinedTextField(
                    value = link,
                    onValueChange = { if (it.length <= MAX_PROFILE_BYTES * 2) link = it; invalidLink = false },
                    label = { Text(stringResource(R.string.apply_mode_link)) },
                    isError = invalidLink,
                    supportingText = if (invalidLink) ({ Text(stringResource(R.string.profile_invalid)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 2, maxLines = 5,
                )
            },
            dismissButton = { TextButton(onClick = { importLink = false }) { Text(stringResource(R.string.cancel_action)) } },
            confirmButton = {
                TextButton(onClick = {
                    val profile = runCatching { decodePortableProfile(link) }.getOrNull()
                    if (profile == null) invalidLink = true else {
                        importLink = false
                        link = ""
                        perform { store.saveProfile(profile); snackbar.showSnackbar(context.getString(R.string.profile_imported)) }
                    }
                }) { Text(stringResource(R.string.profile_import)) }
            },
        )
    }
}

@Composable
private fun ProfileMenuAction(
    icon: ImageVector, label: Int, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.45f)
        destructive -> colors.error
        else -> colors.primary
    }
    Surface(
        onClick = onClick, enabled = enabled, color = colors.surfaceVariant,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Text(stringResource(label), color = if (destructive || !enabled) accent else colors.onSurface,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ProfileEditor(profile: ProxyProfile, existing: Boolean, onDismiss: () -> Unit, onSave: (ProxyProfile) -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var host by rememberSaveable { mutableStateOf(profile.host.takeIf(::isIpv4Input).orEmpty()) }
    var port by rememberSaveable { mutableStateOf(profile.port) }
    var pool by rememberSaveable { mutableIntStateOf(profile.poolSize) }
    var cloudflare by rememberSaveable { mutableStateOf(profile.cloudflare) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val normalized = normalizedIpv4(host)
    val hostError = if (normalized == null && (submitted || !isIpv4Input(profile.host))) R.string.host_invalid else null
    val nameValid = name.trim().length in 1..64 && name.none(Char::isISOControl)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing) R.string.profile_edit else R.string.profile_add)) },
        text = {
            val scroll = androidx.compose.foundation.rememberScrollState()
            Column(
                Modifier.verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProxyInput(name, { name = it }, R.string.profile_name, if (submitted && !nameValid) R.string.profile_invalid_name else null)
                ProxyAddressInput(host, { host = it }, hostError)
                ProxyInput(port, { port = it }, R.string.port, if (submitted && !validPort(port)) R.string.port_invalid_range else null, KeyboardType.Number)
                Text(stringResource(R.string.ws_pool))
                PoolSelector(pool, onSelect = { pool = it })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cloudflare CDN", Modifier.weight(1f))
                    Switch(cloudflare, { cloudflare = it })
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) } },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (normalized != null && nameValid && validPort(port)) onSave(profile.copy(
                    name = name.trim(), host = normalized, port = port.toInt().toString(), poolSize = pool,
                    cloudflare = cloudflare, advanced = profile.advanced + ("is_dc_auto" to cloudflare.toString()),
                ))
            }) { Text(stringResource(R.string.save_action)) }
        },
    )
}

