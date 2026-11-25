package com.amurcanov.tgwsproxy.ui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amurcanov.tgwsproxy.ProxyService
import com.amurcanov.tgwsproxy.isIpv4Input
import com.amurcanov.tgwsproxy.normalizedIpv4
import com.amurcanov.tgwsproxy.validPort
import com.amurcanov.tgwsproxy.validSecret
import com.amurcanov.tgwsproxy.newProxySecret
import com.amurcanov.tgwsproxy.poolSizes
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.amurcanov.tgwsproxy.R
import com.amurcanov.tgwsproxy.SettingsStore
import kotlinx.coroutines.launch

fun openTelegram(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            com.amurcanov.tgwsproxy.R.string.telegram_not_found,
            Toast.LENGTH_SHORT
        ).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTab(settingsStore: SettingsStore, profileId: String, appearanceMenu: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRunning by ProxyService.isRunning.collectAsStateWithLifecycle()
    val isStarting by ProxyService.isStarting.collectAsStateWithLifecycle()
    val locked = isRunning || isStarting
    val snackbar = remember { SnackbarHostState() }

    val isReady by settingsStore.isReady.collectAsStateWithLifecycle(initialValue = false)
    val isExperimental by settingsStore.isExperimentalMode.collectAsStateWithLifecycle(initialValue = false)

    val savedIsDcAuto by settingsStore.isDcAuto.collectAsStateWithLifecycle(initialValue = true)
    val savedDc1 by settingsStore.dc1.collectAsStateWithLifecycle(initialValue = "")
    val savedDc2 by settingsStore.dc2.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_DIRECT_DC2_IP)
    val savedDc3 by settingsStore.dc3.collectAsStateWithLifecycle(initialValue = "")
    val savedDc4 by settingsStore.dc4.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_DIRECT_DC4_IP)
    val savedDc5 by settingsStore.dc5.collectAsStateWithLifecycle(initialValue = "")
    val savedDc203 by settingsStore.dc203.collectAsStateWithLifecycle(initialValue = "")
    val savedDc1m by settingsStore.dc1m.collectAsStateWithLifecycle(initialValue = "")
    val savedDc2m by settingsStore.dc2m.collectAsStateWithLifecycle(initialValue = "")
    val savedDc3m by settingsStore.dc3m.collectAsStateWithLifecycle(initialValue = "")
    val savedDc4m by settingsStore.dc4m.collectAsStateWithLifecycle(initialValue = "")
    val savedDc5m by settingsStore.dc5m.collectAsStateWithLifecycle(initialValue = "")
    val savedDc203m by settingsStore.dc203m.collectAsStateWithLifecycle(initialValue = "")
    val savedPort by settingsStore.port.collectAsStateWithLifecycle(initialValue = "1443")
    val savedBindIp by settingsStore.bindIp.collectAsStateWithLifecycle(initialValue = "127.0.0.1")
    val savedPoolSize by settingsStore.poolSize.collectAsStateWithLifecycle(initialValue = 4)
    val savedCfEnabled by settingsStore.cfproxyEnabled.collectAsStateWithLifecycle(initialValue = true)
    val savedCustomDomainEnabled by settingsStore.customCfDomainEnabled.collectAsStateWithLifecycle(initialValue = false)
    val savedCustomDomain by settingsStore.customCfDomain.collectAsStateWithLifecycle(initialValue = "")
    val autoStartOnBoot by settingsStore.autoStartOnBoot.collectAsStateWithLifecycle(initialValue = false)
    val savedSecretKey by settingsStore.secretKey.collectAsStateWithLifecycle(initialValue = "LOADING")

    if (!isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
        return
    }

    var isDcAuto by rememberSaveable(savedIsDcAuto) { mutableStateOf(savedIsDcAuto) }
    var experimentalMode by rememberSaveable(isExperimental) { mutableStateOf(isExperimental) }
    var dc1Text by rememberSaveable(savedDc1) { mutableStateOf(savedDc1) }
    var dc2Text by rememberSaveable(savedDc2) { mutableStateOf(savedDc2) }
    var dc3Text by rememberSaveable(savedDc3) { mutableStateOf(savedDc3) }
    var dc4Text by rememberSaveable(savedDc4) { mutableStateOf(savedDc4) }
    var dc5Text by rememberSaveable(savedDc5) { mutableStateOf(savedDc5) }
    var dc203Text by rememberSaveable(savedDc203) { mutableStateOf(savedDc203) }
    var dc1mText by rememberSaveable(savedDc1m) { mutableStateOf(savedDc1m) }
    var dc2mText by rememberSaveable(savedDc2m) { mutableStateOf(savedDc2m) }
    var dc3mText by rememberSaveable(savedDc3m) { mutableStateOf(savedDc3m) }
    var dc4mText by rememberSaveable(savedDc4m) { mutableStateOf(savedDc4m) }
    var dc5mText by rememberSaveable(savedDc5m) { mutableStateOf(savedDc5m) }
    var dc203mText by rememberSaveable(savedDc203m) { mutableStateOf(savedDc203m) }

    var portText by rememberSaveable(savedPort) { mutableStateOf(savedPort) }
    var bindIpText by rememberSaveable(savedBindIp) {
        mutableStateOf(savedBindIp.takeIf(::isIpv4Input).orEmpty())
    }
    var selectedPoolSize by rememberSaveable(savedPoolSize) { mutableIntStateOf(savedPoolSize) }
    var cfEnabled by rememberSaveable(savedCfEnabled) { mutableStateOf(savedCfEnabled) }
    var customCfDomainEnabled by rememberSaveable(savedCustomDomainEnabled) { mutableStateOf(savedCustomDomainEnabled) }
    var customCfDomain by rememberSaveable(savedCustomDomain) { mutableStateOf(savedCustomDomain) }
    var secretKeyText by rememberSaveable(savedSecretKey) { mutableStateOf(savedSecretKey.takeUnless { it == "LOADING" }.orEmpty()) }
    var secretVisible by remember { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    var showIpSetupDialog by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showIpSetupDialog) {
        IpSetupDialog(
            isExperimental = experimentalMode,
            onExperimentalChange = { experimentalMode = it },
            dc1Text = dc1Text, onDc1Change = { dc1Text = it },
            dc2Text = dc2Text, onDc2Change = { dc2Text = it },
            dc3Text = dc3Text, onDc3Change = { dc3Text = it },
            dc4Text = dc4Text, onDc4Change = { dc4Text = it },
            dc5Text = dc5Text, onDc5Change = { dc5Text = it },
            dc203Text = dc203Text, onDc203Change = { dc203Text = it },
            dc1mText = dc1mText, onDc1mChange = { dc1mText = it },
            dc2mText = dc2mText, onDc2mChange = { dc2mText = it },
            dc3mText = dc3mText, onDc3mChange = { dc3mText = it },
            dc4mText = dc4mText, onDc4mChange = { dc4mText = it },
            dc5mText = dc5mText, onDc5mChange = { dc5mText = it },
            dc203mText = dc203mText, onDc203mChange = { dc203mText = it },
            onDismiss = { showIpSetupDialog = false }
        )
    }

    val normalized = normalizedIpv4(bindIpText)
    val hostError = if (normalized == null && (submitted || !isIpv4Input(savedBindIp))) R.string.host_invalid else null
    val portError = if (submitted && !validPort(portText)) R.string.port_invalid_range else null
    val secretError = if (submitted && !validSecret(secretKeyText)) R.string.secret_invalid else null

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
                .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 68.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                appearanceMenu()
            }
            AppSectionCard {
                SettingTitle(Icons.Default.Public, stringResource(R.string.connection))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 380.dp || LocalDensity.current.fontScale >= 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProxyAddressInput(bindIpText, { bindIpText = it }, hostError, enabled = !locked)
                            ProxyInput(portText, { portText = it }, R.string.port, portError, KeyboardType.Number, enabled = !locked)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProxyAddressInput(bindIpText, { bindIpText = it }, hostError, Modifier.weight(2f), !locked)
                            ProxyInput(portText, { portText = it }, R.string.port, portError, KeyboardType.Number, Modifier.weight(1f), !locked)
                        }
                    }
                }
                Text(stringResource(R.string.connection_mode, if (cfEnabled) "Cloudflare CDN" else stringResource(R.string.direct_mode)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!cfEnabled) {
                    OutlinedButton(onClick = { showIpSetupDialog = true }, enabled = !locked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.configure_dc_addresses))
                    }
                }
            }

            AppSectionCard {
                SettingTitle(Icons.Default.Layers, stringResource(R.string.ws_pool))
                PoolSelector(selectedPoolSize, enabled = !locked) { selectedPoolSize = it }
            }

            AppSectionCard {
                SettingTitle(Icons.Default.VpnKey, stringResource(R.string.secret_key))
                ProxyInput(
                    secretKeyText, { secretKeyText = it }, R.string.secret_key, secretError,
                    KeyboardType.Password, enabled = !locked, masked = !secretVisible,
                    trailingIcon = {
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                stringResource(if (secretVisible) R.string.hide_secret else R.string.show_secret))
                        }
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = validSecret(secretKeyText),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Proxy secret", secretKeyText)
                            if (android.os.Build.VERSION.SDK_INT >= 24) {
                                clip.description.extras = android.os.PersistableBundle().apply {
                                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                                }
                            }
                            clipboard.setPrimaryClip(clip)
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.copied)) }
                        },
                    ) { Text(stringResource(R.string.copy)) }
                    TextButton(onClick = { secretKeyText = newProxySecret() }, enabled = !locked,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.regenerate_key)) }
                }
            }

            AppSectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingTitle(Icons.Default.Cloud, "Cloudflare CDN")
                        Text(stringResource(R.string.cf_setting_hint), style = MaterialTheme.typography.bodySmall)
                        ObservedRoute()
                    }
                    SettingsSwitch(label = "Cloudflare CDN", checked = cfEnabled, enabled = !locked, onCheckedChange = {
                        cfEnabled = it
                        isDcAuto = it
                    })
                }
            }

            AppSectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingTitle(Icons.Default.PowerSettingsNew, stringResource(R.string.autostart))
                        Text(stringResource(R.string.autostart_setting_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    SettingsSwitch(label = stringResource(R.string.autostart), checked = autoStartOnBoot, onCheckedChange = {
                        scope.launch { settingsStore.saveAutoStartOnBoot(it) }
                    })
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !locked,
                onClick = {
                    submitted = true
                    if (normalized != null && validPort(portText) && validSecret(secretKeyText)) {
                        scope.launch {
                            try {
                            settingsStore.saveAll(
                                profileId, isDcAuto, dc1Text, dc2Text, dc3Text, dc4Text, dc5Text, dc203Text,
                                dc1mText, dc2mText, dc3mText, dc4mText, dc5mText, dc203mText,
                                experimentalMode, normalized, portText.toInt().toString(), selectedPoolSize,
                                cfEnabled, customCfDomainEnabled, customCfDomain, secretKeyText.lowercase()
                            )
                            snackbar.showSnackbar(context.getString(R.string.settings_saved))
                            } catch (cancel: kotlinx.coroutines.CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                snackbar.showSnackbar(context.getString(R.string.profile_operation_error))
                            }
                        }
                    }
                },
            ) { Text(stringResource(R.string.save_action)) }
            if (locked) Text(stringResource(R.string.profile_stop_to_switch), style = MaterialTheme.typography.bodySmall)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun ProxyAddressInput(
    value: String,
    onChange: (String) -> Unit,
    error: Int?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ProxyInput(
        value = value,
        onChange = { input -> if (isIpv4Input(input)) onChange(input) },
        label = R.string.host_label,
        error = error,
        keyboard = KeyboardType.Decimal,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
internal fun ProxyInput(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    error: Int? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    masked: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { id -> { Text(stringResource(id)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            cursorColor = colors.primary,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outlineVariant,
        ),
    )
}

@Composable
internal fun PoolSelector(selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val colors = MaterialTheme.colorScheme
        poolSizes.forEach { count ->
            val active = selected == count
            val background by animateColorAsState(
                if (active) colors.primaryContainer else colors.surfaceVariant,
                tween(200), label = "pool_chip",
            )
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .selectable(active, enabled, role = Role.RadioButton) { onSelect(count) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingTitle(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(150),
        label = "switch_check",
    )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            checkedThumbColor = Color.White,
            checkedIconColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        thumbContent = {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
                    .scale(0.75f + checkProgress * 0.25f)
                    .alpha(checkProgress),
            )
        },
    )
}

@Composable
private fun IpSetupDialog(
    isExperimental: Boolean,
    onExperimentalChange: (Boolean) -> Unit,
    dc1Text: String, onDc1Change: (String) -> Unit,
    dc2Text: String, onDc2Change: (String) -> Unit,
    dc3Text: String, onDc3Change: (String) -> Unit,
    dc4Text: String, onDc4Change: (String) -> Unit,
    dc5Text: String, onDc5Change: (String) -> Unit,
    dc203Text: String, onDc203Change: (String) -> Unit,
    dc1mText: String, onDc1mChange: (String) -> Unit,
    dc2mText: String, onDc2mChange: (String) -> Unit,
    dc3mText: String, onDc3mChange: (String) -> Unit,
    dc4mText: String, onDc4mChange: (String) -> Unit,
    dc5mText: String, onDc5mChange: (String) -> Unit,
    dc203mText: String, onDc203mChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val onIpChange = { newValue: String, update: (String) -> Unit ->
        update(newValue.filter { it.isDigit() || it == '.' })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(com.amurcanov.tgwsproxy.R.string.dc_addresses),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                @Composable
                fun dcInput(label: String, value: String, update: (String) -> Unit) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = { onIpChange(it, update) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isExperimental) {
                        dcInput("DC1", dc1Text, onDc1Change)
                        dcInput("DC2", dc2Text, onDc2Change)
                        dcInput("DC3", dc3Text, onDc3Change)
                        dcInput("DC4", dc4Text, onDc4Change)
                        dcInput("DC5", dc5Text, onDc5Change)
                        dcInput("DC203", dc203Text, onDc203Change)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text(stringResource(com.amurcanov.tgwsproxy.R.string.media_dcs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        dcInput("DC1m", dc1mText, onDc1mChange)
                        dcInput("DC2m", dc2mText, onDc2mChange)
                        dcInput("DC3m", dc3mText, onDc3mChange)
                        dcInput("DC4m", dc4mText, onDc4mChange)
                        dcInput("DC5m", dc5mText, onDc5mChange)
                        dcInput("DC203m", dc203mText, onDc203mChange)
                    } else {
                        dcInput("DC2", dc2Text, onDc2Change)
                        dcInput("DC4", dc4Text, onDc4Change)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(com.amurcanov.tgwsproxy.R.string.experimental_mode),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = isExperimental,
                        onCheckedChange = onExperimentalChange
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(stringResource(com.amurcanov.tgwsproxy.R.string.done), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
