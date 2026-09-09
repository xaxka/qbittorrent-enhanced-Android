package io.github.xixka.qbittorrent.ui.rss

import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.model.RssArticle
import io.github.xixka.qbittorrent.model.RssFeedNode

/**
 * Parses the dynamic JSON tree of `GET /api/v2/rss/items` into [RssFeedNode]s.
 *
 * Shape (qBitController RssFeedNodeSerializer parity):
 *  - a folder is a plain object whose keys are child items
 *  - a feed is an object containing a string "uid" (plus url / articles)
 */
object RssTreeParser {

    fun parse(root: JsonObject): List<RssFeedNode> =
        parseLevel(root, emptyList())

    private fun parseLevel(obj: JsonObject, parentPath: List<String>): List<RssFeedNode> {
        val result = mutableListOf<RssFeedNode>()
        for ((key, value) in obj.entrySet()) {
            val child = value as? JsonObject ?: continue
            val uid = child.get("uid")?.takeIf { it.isJsonPrimitive }?.asString
            if (uid != null) {
                val url = child.get("url")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                val articles: List<RssArticle> =
                    child.get("articles")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el ->
                            val a = el as? JsonObject ?: return@mapNotNull null
                            runCatching { parseArticle(a) }.getOrNull()
                        }
                        ?: emptyList()
                result.add(
                    RssFeedNode(
                        name = key,
                        uid = uid,
                        url = url,
                        children = emptyList(),
                        path = parentPath,
                        articles = articles,
                        hasUnread = articles.any { !it.isRead },
                    )
                )
            } else {
                val children = parseLevel(child, parentPath + key)
                result.add(
                    RssFeedNode(
                        name = key,
                        uid = null,
                        url = null,
                        children = children,
                        path = parentPath,
                        hasUnread = children.any { it.hasUnread },
                    )
                )
            }
        }
        // folders first, then feeds — qBC sorts everything alphabetically;
        // LibreTorrent groups folders above feeds
        return result.sortedWith(
            compareBy<RssFeedNode> { !it.isFeed }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    private fun parseArticle(a: JsonObject): RssArticle {
        val torrentUrl = (a.get("torrentURL") ?: a.get("torrentUrl"))
            ?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        return RssArticle(
            id = a.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
            title = a.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
            description = a.get("description")?.takeIf { it.isJsonPrimitive }?.asString,
            torrentUrl = torrentUrl,
            link = a.get("link")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
            isRead = a.get("isRead")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            date = parseArticleDate(a.get("date")),
        )
    }

    /**
     * The engine serializes article dates with `QDateTime::toString(
     * Qt::RFC2822Date)` — e.g. "09 Sep 2026 21:00:00 +0800" (a STRING).
     * Gson's asLong throws on that, which used to drop every article from
     * runCatching and show an empty feed. Returns SECONDS since epoch
     * (the model contract — consumers multiply by 1000). Numeric dates are
     * still accepted as-is.
     */
    private fun parseArticleDate(el: com.google.gson.JsonElement?): Long {
        val primitive = el?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return 0L
        if (primitive.isNumber) return primitive.asLong
        val text = primitive.asString.trim()
        if (text.isEmpty()) return 0L
        for (format in RFC2822_FORMATS) {
            try {
                // SimpleDateFormat works in milliseconds — convert to seconds
                return (java.text.SimpleDateFormat(format, java.util.Locale.US)
                    .parse(text)?.time ?: 0L) / 1000
            } catch (_: java.text.ParseException) {
                // try the next pattern
            }
        }
        return 0L
    }

    private val RFC2822_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", // RFC 2822 with weekday
        "dd MMM yyyy HH:mm:ss Z",      // Qt RFC2822Date (no weekday)
        "dd MMM yyyy HH:mm:ss zzz",   // named zone, e.g. GMT+08:00
    )

    /** All feeds of the tree, flattened, as (path, node) pairs. */
    fun flatten(nodes: List<RssFeedNode>): List<RssFeedNode> {
        val out = mutableListOf<RssFeedNode>()
        fun walk(list: List<RssFeedNode>) {
            for (n in list) {
                if (n.isFeed) out.add(n) else walk(n.children)
            }
        }
        walk(nodes)
        return out
    }

    /**
     * Articles of one node addressed by its API path: a feed's own list, or
     * for a folder every article of its subtree (qBC navigates to the
     * articles screen for folders too — "all articles" of that branch).
     */
    fun subtreeArticles(nodes: List<RssFeedNode>, apiPath: String): List<RssArticle> {
        val node = findNode(nodes, apiPath) ?: return emptyList()
        if (node.isFeed) return node.articles
        val out = mutableListOf<RssArticle>()
        fun walk(children: List<RssFeedNode>) {
            for (c in children) {
                if (c.isFeed) out.addAll(c.articles) else walk(c.children)
            }
        }
        walk(node.children)
        return out
    }

    private fun findNode(nodes: List<RssFeedNode>, apiPath: String): RssFeedNode? {
        for (n in nodes) {
            if (n.apiPath == apiPath) return n
            findNode(n.children, apiPath)?.let { return it }
        }
        return null
    }
}
