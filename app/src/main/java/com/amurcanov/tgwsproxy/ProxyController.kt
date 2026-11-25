package com.amurcanov.tgwsproxy

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat

object ProxyController {

    suspend fun startFromSavedSettings(
        context: Context,
        showInvalidPortToast: Boolean = false
    ): Boolean {
        val settingsStore = SettingsStore(context)
        settingsStore.migrateLegacyDefaults()
        val profile = settingsStore.activeProfileForStart()
        val port = profile.port.toIntOrNull()
        if (port == null || port !in 1..65535) {
            if (showInvalidPortToast) {
                Toast.makeText(context, context.getString(R.string.invalid_port), Toast.LENGTH_SHORT).show()
            }
            ProxyTileService.requestSync(context)
            return false
        }

        val advanced = profile.advanced
        val isExperimental = advanced["is_experimental_mode"] == "true"
        val isDcAuto = advanced["is_dc_auto"]?.toBoolean() ?: true
        val cfEnabled = profile.cloudflare
        val customCfDomainEnabled = advanced["custom_cf_domain_enabled"] == "true"
        val customCfDomain = advanced["custom_cf_domain"].orEmpty().trim()

        val parsedIps = buildList {
            if (!isDcAuto) {
                appendDc(1, advanced["dc1"].orEmpty())
                appendDc(2, advanced["dc2"] ?: SettingsStore.DEFAULT_DIRECT_DC2_IP)
                appendDc(3, advanced["dc3"].orEmpty())
                appendDc(4, advanced["dc4"] ?: SettingsStore.DEFAULT_DIRECT_DC4_IP)
                if (isExperimental) {
                    for (dc in listOf(5, 203)) appendDc(dc, advanced["dc" + dc].orEmpty())
                    for (dc in listOf(1, 2, 3, 4, 5, 203)) appendDc(-dc, advanced["dc" + dc + "m"].orEmpty())
                }
            }
        }.joinToString(",")

        ContextCompat.startForegroundService(
            context,
            Intent(context, ProxyService::class.java).apply {
                action = ProxyService.ACTION_START
                putExtra(ProxyService.EXTRA_BIND_IP, profile.host)
                putExtra(ProxyService.EXTRA_PORT, port)
                putExtra(ProxyService.EXTRA_IPS, parsedIps)
                putExtra(ProxyService.EXTRA_POOL_SIZE, profile.poolSize)
                putExtra(ProxyService.EXTRA_CFPROXY_ENABLED, cfEnabled)
                putExtra(ProxyService.EXTRA_CFPROXY_PRIORITY, true)
                putExtra(
                    ProxyService.EXTRA_CFPROXY_DOMAIN,
                    if (customCfDomainEnabled && cfEnabled) customCfDomain else ""
                )
                putExtra(ProxyService.EXTRA_SECRET_KEY, profile.secret.trim())
            }
        )
        ProxyTileService.requestSync(context)
        return true
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, ProxyService::class.java).apply {
                action = ProxyService.ACTION_STOP
            }
        )
        ProxyTileService.requestSync(context)
    }

    private fun MutableList<String>.appendDc(dc: Int, value: String) {
        val ip = value.trim()
        if (ip.isNotBlank()) {
            add("$dc:$ip")
        }
    }

}
