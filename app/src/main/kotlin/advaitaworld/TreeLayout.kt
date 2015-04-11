package advaitaworld

import advaitaworld.parsing.CommentNode

public data class ItemInfo(
        val indentLevel: Int,
        val node: CommentNode,
        /**
         * Whether this item is part of 'staircase' - a flow of 1-to-1 reply conversation between only two users
         */
        val isInStaircase: Boolean
)

/**
 * Analyzes a passed node tree and produces a set of [ItemInfo] objects which can be used
 * in adapter to present a plain list of comment items
 *
 * @param root a root of the tree to unfold to a plain list
 */
public fun buildTreeLayout(root: CommentNode, startIndent: Int = 0) : List<ItemInfo> {
    val items : MutableList<ItemInfo> = arrayListOf()

    items.add(ItemInfo(startIndent, root, isInStaircase = root.children.size() == 1))

    // merge the 'staircase' of single-replies into a same indent level
    var n = root
    while(n.children.size() == 1) {
        val child = n.children.first()
        items.add(ItemInfo(startIndent, child, isInStaircase = true))
        n = child
    }

    // after 'staircase' stopped or even not even started - rest of the children are
    // indented to the next level, each followed by expand sections
    for(child in n.children) {
        items.add(ItemInfo(startIndent + 1, child, isInStaircase = false))
    }
    return items
}

/**
 * Analyzes a passed node information and produces a set of [ItemInfo] objects which can be used
 * in adapter to present a plain list of comment items.
 *
 * This overload prepares a display info for the case where a list of sibling nodes is designated
 * as a primary content, rather than a tree emanating from the single root
 *
 * @param nodes a list of sibling nodes
 */
public fun buildTreeLayout(nodes: List<CommentNode>, startIndent: Int = 0) : List<ItemInfo> {
    // in this case there is no support for 'staircasing', simple list of siblings...
    return nodes.map { ItemInfo(startIndent, it, isInStaircase = false) }
}
