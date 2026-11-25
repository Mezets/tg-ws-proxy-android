package com.amurcanov.tgwsproxy

import android.util.Base64
import java.net.IDN
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

internal const val DEFAULT_PROFILE_ID = "default"
internal const val MAX_PROFILE_BYTES = 16384
internal val poolSizes = listOf(2, 4, 6, 8)

internal fun validPort(value: String) = value.toIntOrNull() in 1..65535
internal fun validSecret(value: String) = value.length == 32 && value.all {
    it in '0'..'9' || it.lowercaseChar() in 'a'..'f'
}

internal fun isIpv4Input(value: String) = value.all { it in '0'..'9' || it == '.' }

internal fun normalizedIpv4(value: String): String? {
    val candidate = value.trim()
    if (candidate.length !in 7..15 || !isIpv4Input(candidate)) return null
    val parts = candidate.split('.')
    if (parts.size != 4) return null
    return parts.map { part ->
        if (part.length !in 1..3) return null
        part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }.joinToString(".")
}

internal fun normalizedHost(value: String): String? {
    val candidate = value.trim()
    if (candidate.isEmpty() || candidate.length > 253) return null
    if (isIpv4Input(candidate)) {
        val parts = candidate.split('.')
        return candidate.takeIf { parts.size == 4 && parts.all { part -> part.toIntOrNull() in 0..255 } }
    }
    val ascii = runCatching { IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES) }.getOrNull()
        ?.lowercase(Locale.ROOT) ?: return null
    if (ascii.length > 253) return null
    return ascii.takeIf { host -> host.removeSuffix(".").split('.').all { it.length in 1..63 } }
}

internal fun newProxySecret(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

internal data class ProxyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "127.0.0.1",
    val port: String = "1443",
    val poolSize: Int = 4,
    val cloudflare: Boolean = true,
    val secret: String = "",
    val advanced: Map<String, String> = emptyMap(),
) {
    fun encode(includeSecret: Boolean = true): JSONObject = JSONObject().apply {
        put("name", name)
        put("host", host)
        put("port", port)
        put("pool", poolSize)
        put("cloudflare", cloudflare)
        if (includeSecret && secret.isNotEmpty()) put("secret", secret)
        put("advanced", JSONObject(advanced))
    }

    companion object {
        val advancedKeys = setOf(
            "is_dc_auto", "dc1", "dc2", "dc3", "dc4", "dc5", "dc203",
            "dc1m", "dc2m", "dc3m", "dc4m", "dc5m", "dc203m",
            "is_experimental_mode", "custom_cf_domain_enabled", "custom_cf_domain",
        )
        val booleanKeys = setOf("is_dc_auto", "is_experimental_mode", "custom_cf_domain_enabled")

        fun decode(value: JSONObject, id: String = UUID.randomUUID().toString()): ProxyProfile {
            val advanced = value.optJSONObject("advanced")
            return ProxyProfile(
                id = id,
                name = value.getString("name").trim(),
                host = value.getString("host"),
                port = value.getString("port"),
                poolSize = value.getInt("pool"),
                cloudflare = value.getBoolean("cloudflare"),
                secret = value.optString("secret"),
                advanced = advancedKeys.mapNotNull { key ->
                    advanced?.opt(key)?.let { key to it.toString() }
                }.toMap(),
            )
        }
    }
}

internal data class ProfilesState(val activeId: String, val entries: List<ProxyProfile>) {
    val active: ProxyProfile? get() = entries.firstOrNull { it.id == activeId }
}

internal fun encodePortableProfile(profile: ProxyProfile, includeSecret: Boolean): String =
    JSONObject().put("format", 1).put("profile", profile.encode(includeSecret)).toString()

internal fun profileShareLink(payload: String): String =
    "tgwsproxy://profile/" + Base64.encodeToString(
        payload.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

internal fun decodePortableProfile(input: String): ProxyProfile {
    val value = input.trim()
    val prefix = "tgwsproxy://profile/"
    require(value.length <= MAX_PROFILE_BYTES * 2)
    val raw = if (value.startsWith(prefix)) {
        Base64.decode(value.removePrefix(prefix), Base64.URL_SAFE or Base64.NO_WRAP).toString(Charsets.UTF_8)
    } else value
    require(raw.toByteArray(Charsets.UTF_8).size <= MAX_PROFILE_BYTES)
    val json = JSONObject(raw)
    require(json.getInt("format") == 1)
    val profile = ProxyProfile.decode(json.getJSONObject("profile"))
    val host = requireNotNull(normalizedIpv4(profile.host))
    require(profile.name.length in 1..64 && profile.name.none(Char::isISOControl))
    require(validPort(profile.port) && profile.poolSize in poolSizes)
    require(profile.secret.isEmpty() || validSecret(profile.secret))
    require(profile.advanced.all { (key, value) ->
        if (key in ProxyProfile.booleanKeys) value == "true" || value == "false"
        else value.isEmpty() || normalizedHost(value) != null
    })
    return profile.copy(host = host, port = profile.port.toInt().toString())
}

