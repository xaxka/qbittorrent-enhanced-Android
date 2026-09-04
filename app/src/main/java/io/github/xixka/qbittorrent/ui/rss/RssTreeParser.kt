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
                val articles = child.get("articles")?.takeIf { it.isJsonArray }?.let { arr ->
                    arr.mapNotNull { el ->
                        val a = el as? JsonObject ?: return@mapNotNull null
                        runCatching { parseArticle(a) }.getOrNull()
                    }
                } ?: emptyList()
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

    private fun parseArticle(a: JsonObject): RssArticle = RssArticle(
        id = a.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
        title = a.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
        description = a.get("description")?.takeIf { it.isJsonPrimitive }?.asString,
        torrentUrl = a.get("torrentUrl")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
        link = a.get("link")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
        isRead = a.get("isRead")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
        date = a.get("date")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
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
}
