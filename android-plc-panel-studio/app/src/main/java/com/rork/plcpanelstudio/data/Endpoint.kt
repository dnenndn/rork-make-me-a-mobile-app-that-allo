package com.rork.plcpanelstudio.data

/** Host and port of a bridge server. */
data class Endpoint(val host: String, val port: Int)

object EndpointParser {
    /** The bridge server listens on this port unless started with --port. */
    const val DEFAULT_PORT = 8080

    /**
     * Understands what people actually type or paste for a bridge address:
     * `192.168.1.129`, `192.168.1.129:8081`, `http://192.168.1.129:8081/`, `pc-name.local:8081`.
     * A port written inside the address wins over [portField]; if neither has a usable port,
     * [defaultPort] is used. Returns null when the address is not usable.
     */
    fun parse(hostInput: String, portField: String, defaultPort: Int = DEFAULT_PORT): Endpoint? {
        var text = hostInput.trim()
        text = text.substringAfter("://")
        text = text.substringBefore('/').substringBefore('?').trim()

        var host = text
        var embeddedPort: Int? = null
        if (text.count { it == ':' } == 1) {
            val colon = text.indexOf(':')
            host = text.substring(0, colon)
            val portText = text.substring(colon + 1)
            if (portText.isNotEmpty()) {
                embeddedPort = portText.toIntOrNull() ?: return null
            }
        }

        host = host.trim()
        if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) return null

        val port = embeddedPort ?: portField.trim().toIntOrNull() ?: defaultPort
        if (port !in 1..65535) return null
        return Endpoint(host, port)
    }
}

/**
 * Plain-language advice for a failed bridge connection, based on the short reason the
 * bridge client reports. Returns null when there is nothing useful to add.
 */
fun connectionHint(reason: String?): String? {
    if (reason == null) return null
    // A device with a backup address reports both reasons: "Timeout (backup: Timeout)".
    val main = reason.substringBefore(" (backup:")
    if (main != reason) {
        return "Neither address answered. Check that the bridge is running, that the phone is on the " +
            "same Wi-Fi as the computer or has Tailscale turned on, and that the firewall allows the port."
    }
    return singleAddressHint(reason)
}

private fun singleAddressHint(reason: String): String? = when (reason) {
    "Timeout" ->
        "No answer. Make sure the phone is on the same Wi-Fi as the bridge computer and that " +
            "the computer's firewall allows this port."
    "Connection refused" ->
        "The computer answered, but nothing is listening on this port. Check that the port " +
            "matches the one the bridge printed when it started."
    "Host not found" ->
        "That address can't be found. Check it for typing mistakes."
    "Network unreachable", "No route to host" ->
        "The phone has no route to this address. Is it on the same Wi-Fi as the computer " +
            "(not mobile data, a VPN or another network)?"
    else -> null
}
