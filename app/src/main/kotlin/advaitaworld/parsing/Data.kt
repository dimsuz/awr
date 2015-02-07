package advaitaworld.parsing

import rx.Observable

public data class ContentInfo(val author: String,
                              val text: CharSequence,
                              val dateString: String,
                              val rating: String?)

public data class ShortPostInfo(
        val content: ContentInfo,
        val commentCount: String?)

public data class CommentNode(val content: ContentInfo,
                              val children: List<CommentNode>?)

public data class PostData(val content: ContentInfo,
                           val comments: List<CommentNode>)

public data class User(val name: String, val avatarUrl: String)

