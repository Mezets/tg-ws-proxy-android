package com.amurcanov.tgwsproxy

internal object LogPrivacy {
    private val urls = Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s<>"']+""")
    private val credentials = Regex("""(?i)\b(secret(?:[-_ ]?key)?|token|password|authorization|api[-_ ]?key)\s*[:=]\s*[^\r\n]+""")
    private val hex = Regex("""(?i)(?<![a-z0-9])[0-9a-f]{32,}(?![a-z0-9])""")
    private val ipv4 = Regex("""(?<![\w.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![\w.])""")
    private val ipv6 = Regex("""(?i)(?<![\w:])[0-9a-f:]*:[0-9a-f:]*:[0-9a-f:]+(?![\w:])""")
    private val hosts = Regex("""(?i)\b(?:[a-z0-9-]+\.)+[a-z][a-z0-9-]{1,}\b""")
    private val paths = Regex("""(?:[A-Za-z]:[\\/]|(?<!\w)/(?:data|storage|sdcard|mnt|home|Users)/)[^\s'"]+""")
    private val accounts = Regex("""(?:\b[\w.+-]+@[\w.-]+|(?<!\w)@[A-Za-z0-9_]{4,})""")
    private val opaque = Regex("""(?<!\w)[A-Za-z0-9_+/=-]{24,}(?!\w)""")

    fun forDisplay(message: String): String {
        val withoutLinks = urls.replace(message) { match ->
            val value = match.value
            if (value.contains("secret=", ignoreCase = true) || value.startsWith("tgwsproxy://", ignoreCase = true)) "[redacted]"
            else value
        }
        return hex.replace(credentials.replace(withoutLinks, "[redacted]"), "[redacted]")
    }

    fun forTransfer(message: String): String {
        var sanitized = forDisplay(message)
        for (pattern in listOf(urls, accounts, paths, ipv4, ipv6, hosts, opaque)) {
            sanitized = pattern.replace(sanitized, "[redacted]")
        }
        return sanitized
    }
}
