package advaitaworld.parsing

public data class ContentInfo(val author: String,
                              val text: CharSequence,
                              val dateString: String,
                              val rating: String?) {
    fun toShortString(maxTextLength: Int) : String {
        return "[$author]: ${text.subSequence(0, Math.min(maxTextLength, text.length()))}"
    }
}

public data class ShortPostInfo(
        val postId : String,
        val title: String,
        val content: ContentInfo,
        val commentCount: String?)

public data class CommentNode(val path: LongArray,
                              val content: ContentInfo,
                              val children: List<CommentNode>,
                              val deepChildCount: Int) {
    public fun id() : Long {
        return path.last()
    }
    public fun parentId() : Long {
        return if(path.size() > 1) path.get(path.lastIndex - 1) else -1
    }
}

public data class PostData(val title: String,
                           val content: ContentInfo,
                           val comments: List<CommentNode>)

/**
 * Represents information common for all AW users
 */
public data class User(val name: String, val avatarUrl: String)

/**
 * Represents information about LOGGED IN user profile
 */
public data class ProfileInfo(val name: String, val email: String, val pictureUrl: String, val securityKey: String)

private val emptyContent = ContentInfo("", "", "", null)
public fun emptyContentInfo() : ContentInfo {
    return emptyContent
}

private val emptyPostData = PostData("", emptyContent, emptyList())
public fun emptyPostData() : PostData {
    return emptyPostData
}

/**
 * Finds a child comment which corresponds to the passed path.
 * Path must be relative to this comment node, i.e. path.first() == this.id()
 */
public fun CommentNode.findByPath(path: LongArray) : CommentNode? {
    if(path.first() != this.id()) {
        // path must be relative to the current comment
        return null
    }
    var node = this
    for(id in path.drop(1)) {
        node = node.children.first { it.id() == id }
    }
    return if(node.id() == path.last()) node else null
}
