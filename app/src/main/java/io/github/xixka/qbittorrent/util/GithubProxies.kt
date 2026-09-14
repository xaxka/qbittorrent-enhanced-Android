package io.github.xixka.qbittorrent.util

/**
 * Built-in GitHub acceleration proxies ("gh mirrors").
 *
 * GitHub release downloads (github.com -> objects.githubusercontent.com) and
 * often even api.github.com are unreachable from mainland-China networks,
 * which used to break the in-app update flow: the update check could find
 * a newer build but the APK download would never complete, and on stricter
 * networks the API call itself timed out. Every update-check and update
 * download URL is now wrapped in a fallback chain — the original URL first
 * (best for users with direct connectivity or a VPN), then the common
 * public proxy mirrors, each used as `"$prefix$githubUrl"`.
 *
 * The last mirror that served a request is remembered for the session and
 * tried before the others, so repeated downloads skip dead mirrors instead
 * of re-probing the whole list.
 */
object GithubProxies {

    /**
     * Common public gh-proxy services, ordered by general reliability.
     * These come and go — a dead one simply fails fast and the chain moves
     * on to the next candidate.
     */
    private val PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/",
        "https://github.moeyy.xyz/",
    )

    /** Session memory of the mirror that last worked (null = none yet). */
    @Volatile
    private var sticky: String? = null

    /**
     * Candidate URLs for [url]: the direct URL first, then the mirrors —
     * the sticky mirror (when set) ahead of the untried ones.
     */
    fun candidates(url: String): List<String> {
        val prefixes = PREFIXES.toMutableList()
        sticky?.let { s -> if (prefixes.remove(s)) prefixes.add(0, s) }
        return listOf(url) + prefixes.map { it + url }
    }

    /**
     * Records that [candidate] successfully served a request originally
     * aimed at [original] (the direct URL). A direct hit keeps the current
     * sticky mirror; a mirror hit promotes that mirror to the front of the
     * next candidate list.
     */
    fun markWorking(candidate: String, original: String) {
        if (candidate != original) {
            candidate.removeSuffix(original).takeIf { it.isNotBlank() }?.let { sticky = it }
        }
    }
}
