package io.github.xixka.qbittorrent.data

/**
 * Connection profile of the remote qBittorrent WebUI API server.
 */
data class ServerConfig(
    val host: String,
    val port: Int = DEFAULT_PORT,
    val https: Boolean = false,
    val basePath: String = "",
    val username: String = "admin",
    val password: String = "",
    val trustAllCerts: Boolean = false,
) {
    val isConfigured: Boolean get() = host.isNotBlank()

    fun baseUrl(): String = buildString {
        append(if (https) "https://" else "http://")
        append(host.trim())
        if (port in 1..65535) append(":").append(port)
        val path = basePath.trim().trim('/')
        if (path.isNotEmpty()) append('/').append(path)
        append('/')
    }

    fun displayHost(): String = buildString {
        append(if (https) "https://" else "http://")
        append(host.trim())
        if (port in 1..65535 && port != DEFAULT_PORT) append(":").append(port)
    }

    companion object {
        const val DEFAULT_PORT = 8080
    }
}
