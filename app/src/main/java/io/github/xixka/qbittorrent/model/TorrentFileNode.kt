package io.github.xixka.qbittorrent.model

/**
 * Torrent content as a folder tree, ported from qBitController's
 * TorrentFileNode (GPL-3.0). The flat /torrents/files list is folded into
 * nested folders; folders aggregate the size / downloaded size / priority
 * of their children (files marked "do not download" are excluded from the
 * aggregates, matching qBC).
 */
sealed class TorrentFileNode(
    open val name: String,
    open val separator: String,
    open val level: Int,
    open val path: String,
) : Comparable<TorrentFileNode> {

    abstract val priority: Int?
    abstract val size: Long
    abstract val downloadedSize: Long
    abstract val progress: Double

    override fun compareTo(other: TorrentFileNode): Int {
        if (this is Folder && other is File) return -1
        if (this is File && other is Folder) return 1
        return name.compareTo(other.name, ignoreCase = true)
    }

    class File(
        val file: TorrentFile,
        override val name: String,
        override val separator: String,
        override val level: Int,
        override val path: String,
    ) : TorrentFileNode(name, separator, level, path) {
        override val priority: Int get() = file.priority
        override val size: Long get() = file.size

        override val downloadedSize: Long
            get() = (file.progressFraction * file.size).toLong()

        override val progress: Double get() = file.progressFraction

        val index: Int get() = file.index
    }

    class Folder(
        override val name: String,
        override val separator: String,
        override val level: Int,
        override val path: String,
        val children: MutableList<TorrentFileNode> = mutableListOf(),
    ) : TorrentFileNode(name, separator, level, path) {

        override val size: Long by lazy {
            children.sumOf { node ->
                if (node.priority != PRIORITY_DO_NOT_DOWNLOAD) node.size else 0L
            }
        }

        override val downloadedSize: Long by lazy {
            children.sumOf { node ->
                if (node.priority != PRIORITY_DO_NOT_DOWNLOAD) node.downloadedSize else 0L
            }
        }

        override val progress: Double by lazy {
            val total = size
            if (total == 0L) 1.0 else downloadedSize.toDouble() / total
        }

        /** null = mixed priorities (qBC semantics); 0 = all skipped. */
        override val priority: Int? by lazy {
            var current: Int? = null
            for (node in children) {
                val p = node.priority ?: return null
                if (current == null) current = p else if (current != p) return null
            }
            current
        }

        fun findChildNode(path: String): TorrentFileNode? {
            if (path.isEmpty()) return this
            val items = path.split(separator)
            var current: TorrentFileNode = this
            for (i in 0 until items.size - 1) {
                current = (current as? Folder)?.children?.find { it.name == items[i] } ?: return null
            }
            return (current as? Folder)?.children?.find { it.name == items.last() }
        }

        /** All file nodes under the given paths (folders are walked deeply). */
        fun findAllFiles(paths: List<String>): List<File> {
            val result = mutableListOf<File>()
            for (p in paths) {
                when (val node = findChildNode(p) ?: continue) {
                    is File -> result.add(node)
                    is Folder -> {
                        val queue = ArrayDeque<Folder>()
                        queue.add(node)
                        while (queue.isNotEmpty()) {
                            for (child in queue.removeFirst().children) {
                                when (child) {
                                    is File -> result.add(child)
                                    is Folder -> queue.add(child)
                                }
                            }
                        }
                    }
                }
            }
            return result
        }
    }

    companion object {
        /** torrents/filePrio: 0 = do not download. */
        const val PRIORITY_DO_NOT_DOWNLOAD = 0

        /**
         * Fold a flat file list into a tree. The separator is auto-detected
         * (/ when any path contains it, backslash otherwise); node paths are
         * always joined with "/" regardless (qBC does the same).
         */
        fun fromFileList(fileList: List<TorrentFile>): Folder {
            val separator = if (fileList.any { it.name.contains('/') }) "/" else "\\"
            val root = Folder(name = "", separator = separator, level = 0, path = "")

            fileList.forEach { file ->
                val pathList = file.name.split(separator)
                var current = root
                for ((i, item) in pathList.withIndex()) {
                    if (i == pathList.lastIndex) {
                        current.children.add(
                            File(
                                file = file,
                                name = item,
                                separator = separator,
                                level = i + 1,
                                path = pathList.joinToString("/"),
                            ),
                        )
                    } else {
                        val existing = current.children.find { it.name == item } as? Folder
                        current = existing ?: Folder(
                            name = item,
                            separator = separator,
                            level = i + 1,
                            path = pathList.take(i + 1).joinToString("/"),
                        ).also { current.children.add(it) }
                    }
                }
            }
            sortNodeTree(root)
            return root
        }

        private fun sortNodeTree(root: Folder) {
            val stack = ArrayDeque<Folder>()
            stack.add(root)
            while (stack.isNotEmpty()) {
                val node = stack.removeFirst()
                node.children.sort()
                node.children.filterIsInstance<Folder>().forEach(stack::add)
            }
        }
    }
}
