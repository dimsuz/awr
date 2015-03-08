package advaitaworld

import advaitaworld.parsing.CommentNode

public enum class ItemType {
    Top
    Reply
    ReplyInStaircase
}

public data class ItemInfo(
        val indentLevel: Int,
        val type: ItemType,
        val node: CommentNode
)

public fun buildTreeLayout(node: CommentNode, startIndent: Int = 0) : List<ItemInfo> {
    val items : MutableList<ItemInfo> = arrayListOf()

    items.add(ItemInfo(startIndent, ItemType.Top, node))

    // merge the 'staircase' of single-replies into a same indent level
    var n = node
    while(n.children.size() == 1) {
        val child = n.children.first()
        items.add(ItemInfo(startIndent, ItemType.ReplyInStaircase, child))
        n = child
    }

    // after 'staircase' stopped or even not even started - rest of the children are
    // indented to the next level, each followed by expand sections
    for(child in n.children) {
        items.add(ItemInfo(startIndent + 1, ItemType.Reply, child))
    }
    return items
}
