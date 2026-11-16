package io.github.avalonskyafar.sancai.interaction

/**
 * Where the pages of an app lie in relation to one another.
 *
 * In Sancai, pages are places rather than a stack. A page is reached by dragging towards it,
 * and a chevron on a row points to where its page lies. The graph records those places; a
 * [SancaiSpatialHost] moves between them.
 *
 * ```kotlin
 * val graph = SancaiSpatialGraph.Builder(root = "main")
 *     .connect("main", Side.BELOW, "settings")      // settings is below main, main above settings
 *     .connect("main", Side.START, "guide")
 *     .connect("main", Side.END, "more")
 *     .connect("settings", Side.END, "advanced")
 *     .child("more", Side.END, "about")             // opened from a row; dragged back, not forth
 *     .build()
 * ```
 *
 * Pages are identified by string keys. The graph is immutable.
 */
public class SancaiSpatialGraph private constructor(
    /** The page everything else is reached from; "back" ends here. */
    public val root: String,
    private val draggable: Map<String, Map<Side, String>>,
    private val placed: Map<String, Map<String, Side>>,
    private val parents: Map<String, String>,
) {
    /** A side of a page. [START] and [END] follow the layout direction. */
    public enum class Side {
        START,
        END,
        ABOVE,
        BELOW;

        /** The side facing this one. */
        public val opposite: Side
            get() = when (this) {
                START -> END
                END -> START
                ABOVE -> BELOW
                BELOW -> ABOVE
            }

        /** Whether moving to this side is a horizontal move. */
        public val isHorizontal: Boolean get() = this == START || this == END
    }

    /** Every page of the graph. */
    public val keys: Set<String> get() = draggable.keys

    public fun contains(key: String): Boolean = draggable.containsKey(key)

    /** The page that a drag from [key] towards [side] reveals, or `null`. */
    public fun neighbour(key: String, side: Side): String? = draggable[key]?.get(side)

    /**
     * The side of [from] on which [to] lies, or `null` when the two are not adjacent. This
     * includes pages placed with [Builder.child], which cannot be reached by dragging.
     */
    public fun sideOf(from: String, to: String): Side? {
        draggable[from]?.forEach { (side, key) -> if (key == to) return side }
        return placed[from]?.get(to)
    }

    /** Where "back" leads from [key]; `null` for the root. */
    public fun parentOf(key: String): String? = parents[key]

    /** Collects connections and builds a validated graph. */
    public class Builder(private val root: String) {
        private val draggable = LinkedHashMap<String, MutableMap<Side, String>>()
        private val placed = HashMap<String, MutableMap<String, Side>>()
        private val parents = HashMap<String, String>()

        init {
            draggable[root] = HashMap()
        }

        private fun page(key: String): MutableMap<Side, String> = draggable.getOrPut(key) { HashMap() }

        private fun drag(from: String, side: Side, to: String) {
            val existing = page(from)[side]
            require(existing == null || existing == to) {
                "\"$from\" already has \"$existing\" on its $side side; cannot also put \"$to\" there"
            }
            page(from)[side] = to
            page(to)
        }

        private fun adopt(parent: String, child: String) {
            if (child != root && child != parent && !parents.containsKey(child)) parents[child] = parent
        }

        /**
         * Puts [to] on the [side] side of [from]. Both pages can be dragged to from the other
         * unless [reciprocal] is false, in which case only the move from [from] exists.
         *
         * The first page a page is connected from becomes its parent for "back".
         */
        @JvmOverloads
        public fun connect(from: String, side: Side, to: String, reciprocal: Boolean = true): Builder {
            require(from != to) { "A page cannot be its own neighbour: \"$from\"" }
            drag(from, side, to)
            if (reciprocal) {
                drag(to, side.opposite, from)
                adopt(from, to)
            }
            return this
        }

        /**
         * Places [child] on the [side] side of [parent] as a page that is opened from a row or
         * button. It slides in from that side and can be dragged back to [parent], but it
         * cannot be reached by dragging from [parent]. Several children may share one side.
         */
        public fun child(parent: String, side: Side, child: String): Builder {
            require(parent != child) { "A page cannot be its own child: \"$parent\"" }
            page(parent)
            placed.getOrPut(parent) { HashMap() }[child] = side
            drag(child, side.opposite, parent)
            adopt(parent, child)
            return this
        }

        /** Overrides where "back" leads from [key]. */
        public fun parent(key: String, parentKey: String): Builder {
            require(key != root) { "The root page has no parent" }
            parents[key] = parentKey
            return this
        }

        /** Validates and builds the graph. */
        public fun build(): SancaiSpatialGraph {
            for ((key, parent) in parents) {
                require(draggable.containsKey(parent)) { "Parent \"$parent\" of \"$key\" is not a page of the graph" }
                require(draggable.containsKey(key)) { "\"$key\" has a parent but is not a page of the graph" }
            }
            for (key in draggable.keys) {
                if (key == root) continue
                var current: String? = key
                var steps = 0
                while (current != null && current != root) {
                    current = parents[current]
                    steps++
                    require(steps <= draggable.size) { "The parents of \"$key\" form a loop" }
                }
                require(current == root) {
                    "\"$key\" cannot get back to the root \"$root\"; connect it from a page that can, or set parent()"
                }
            }
            return SancaiSpatialGraph(
                root,
                draggable.mapValues { HashMap(it.value) },
                placed.mapValues { HashMap(it.value) },
                HashMap(parents),
            )
        }
    }
}
