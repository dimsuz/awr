package advaitaworld

import advaitaworld.parsing.CommentNode

public data class ItemInfo(
        val indentLevel: Int,
        val node: CommentNode,
        val isInStaircase: Boolean
)

public fun buildTreeLayout(node: CommentNode, startIndent: Int = 0) : List<ItemInfo> {
    val items : MutableList<ItemInfo> = arrayListOf()

    items.add(ItemInfo(startIndent, node, isInStaircase = node.children.size() == 1))

    // merge the 'staircase' of single-replies into a same indent level
    var n = node
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
