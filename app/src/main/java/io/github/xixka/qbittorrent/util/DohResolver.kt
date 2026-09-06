package io.github.xixka.qbittorrent.util

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal DNS-over-HTTPS resolver (RFC 8484 §4.1 "JSON API for DOH"
 * response format, as served by AliDNS `…/resolve`, DNSPod `doh.pub/resolve`
 * and Google `dns.google/resolve`) used for ONE job: resolving the DHT
 * bootstrap router hostnames through an encrypted HTTPS channel.
 *
 * Why this exists: on Chinese carrier networks (China Mobile in particular)
 * the plaintext DNS answers for `dht.libtorrent.org` and
 * `router.bittorrent.com` are poisoned on-path (they resolve to unrelated
 * facebook/twitter/ntt addresses), which kills 2 of the 3 bootstrap routers
 * libtorrent ships with and leaves "DHT nodes: 0". A DoH query is plain
 * HTTPS — nothing on the path can rewrite the answer — so the bootstrap
 * addresses come back correct even on those networks.
 *
 * What it deliberately does NOT claim to fix: DoH only repairs name
 * resolution. CGNAT (no public IPv4) and cross-border UDP QoS still slow
 * down bootstrap/joining; those are transport-layer issues no DNS mode can
 * address.
 *
 * No hardcoded IP literals anywhere: the resolver's output list carries the
 * freshly answered addresses itself, and past attempts at baked-in
 * "belt-and-braces" literals rotted within weeks (three of the four shipped
 * by round-46 no longer pointed at any router, and router.bitcomet.org is
 * NXDOMAIN) — dead weight that pushed live contacts out of the bootstrap
 * list.
 */
object DohResolver {

    /** Reference DHT routers: hostname + the port each one answers on.
     *  The ouinet router is qBittorrent 5.x's own default, built for
     *  censored networks (dropping it made China Mobile worse).
     *  router.bitcomet.org is gone (NXDOMAIN since 2026-09) — dropped. */
    private val BOOTSTRAP_HOSTS = listOf(
        "dht.libtorrent.org" to 25401,
        "dht.transmissionbt.com" to 6881,
        "router.bittorrent.com" to 6881,
        "router.utorrent.com" to 6881,
        "router.bt.ouinet.work" to 6881,
    )

    /** Max entries of the final bootstrap list (hostnames + fresh IPs). */
    private const val MAX_ENTRIES = 30

    /** Whole-of-operation budget; every query also has its own timeout. */
    private const val TOTAL_TIMEOUT_MS = 6_000L

    private const val QUERY_TIMEOUT_MS = 5_000

    /**
     * Resolves the bootstrap routers via [dohUrl] and returns the ready-made
     * `Session\DHTBootstrapNodes` value (comma-separated `host:port` list):
     *
     *  * every hostname (kept — where plain DNS still works it is the most
     *    up-to-date entry),
     *  * up to 2 FRESHLY resolved A-record IPs per hostname (the actual fix:
     *    current IPs instead of rotted literals),
     *  * up to 2 AAAA addresses per hostname — China Mobile often passes
     *    IPv6 to foreign hosts when IPv4 UDP is throttled.
     *
     * Returns null when the DoH server could not answer a SINGLE query
     * (wrong URL, no network, non-JSON endpoint) — callers then fall back to
     * the plain static bootstrap list. One successful hostname is enough to
     * return a list: its fresh IPs and the engine's own resolution of the
     * remaining hostnames carry the first contact.
     */
    suspend fun resolveBootstrapNodes(
        dohUrl: String,
        log: (String) -> Unit = {},
    ): String? {
        val url = dohUrl.trim()
        if (!url.startsWith("https://") || !url.contains('/')) {
            log("doh: ignoring non-https endpoint")
            return null
        }
        return try {
            withTimeout(TOTAL_TIMEOUT_MS) {
                coroutineScope {
                    val answers = BOOTSTRAP_HOSTS.map { (host, port) ->
                        async {
                            // both stacks: China Mobile often passes IPv6
                            // to foreign hosts when IPv4 UDP is throttled
                            val v4 = runCatching { resolveType(url, host, 1) }.getOrDefault(emptyList())
                            val v6 = runCatching { resolveType(url, host, 28) }.getOrDefault(emptyList())
                            Triple(host, port, v4 to v6)
                        }
                    }.awaitAll()
                    buildBootstrapList(answers, log)
                }
            }
        } catch (e: Exception) {
            log("doh: resolution failed (${e.javaClass.simpleName})")
            null
        }
    }

    private fun buildBootstrapList(
        answers: List<Triple<String, Int, Pair<List<String>, List<String>>>>,
        log: (String) -> Unit,
    ): String? {
        if (answers.all { it.third.first.isEmpty() && it.third.second.isEmpty() }) {
            log("doh: endpoint answered none of the bootstrap queries — keeping static bootstrap")
            return null
        }
        val entries = LinkedHashSet<String>(MAX_ENTRIES * 2)
        answers.forEach { (host, port, ips) ->
            val (v4, v6) = ips
            if (v4.isNotEmpty()) log("doh: $host -> ${v4.joinToString(", ")}")
            if (v6.isNotEmpty()) log("doh: $host (v6) -> ${v6.joinToString(", ")}")
            entries.add("$host:$port")
            v4.take(2).forEach { ip -> entries.add("$ip:$port") }
            v6.take(2).forEach { ip -> entries.add("[$ip]:$port") }
        }
        return entries.take(MAX_ENTRIES).joinToString(", ")
    }

    /**
     * One A/AAAA lookup through the JSON DoH API:
     * `GET <dohUrl>?name=<host>&type=<1|28>` -> `{"Status":0,"Answer":[{"type":1,"data":"1.2.3.4"}]}`.
     * Empty list on any error (timeout, non-200, parse failure, NXDOMAIN).
     */
    suspend fun resolveA(dohUrl: String, host: String): List<String> =
        resolveType(dohUrl, host, 1)

    private suspend fun resolveType(dohUrl: String, host: String, type: Int): List<String> =
        withContext(Dispatchers.IO) {
            val sep = if (dohUrl.contains('?')) '&' else '?'
            val target =
                "$dohUrl${sep}name=${URLEncoder.encode(host, "UTF-8")}&type=$type"
            val conn = URL(target).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = QUERY_TIMEOUT_MS
                conn.readTimeout = QUERY_TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/dns-json")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext emptyList()
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseARecords(body, type)
            } catch (e: Exception) {
                emptyList()
            } finally {
                conn.disconnect()
            }
        }

    private fun parseARecords(body: String, type: Int): List<String> = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("Status")?.takeIf { it.isJsonPrimitive }?.asInt != 0) {
            return@runCatching emptyList()
        }
        root.getAsJsonArray("Answer")
            ?.mapNotNull { element ->
                val answer = element.asJsonObject
                // 1 = A, 28 = AAAA (skip CNAME chains, SOA…)
                if (answer.get("type")?.takeIf { it.isJsonPrimitive }?.asInt == type) {
                    answer.get("data")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                } else {
                    null
                }
            }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }.getOrDefault(emptyList())
}
