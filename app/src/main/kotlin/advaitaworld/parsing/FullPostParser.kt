package advaitaworld.parsing

import org.jsoup.Jsoup
import android.text.Html
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Stack

/**
 * Parses a full post
 * @param stream a stream containing the html of the post page
 * @param baseUri a base uri to be used for resolving links
 */
public fun parseFullPost(stream: java.io.InputStream, baseUri: String) : PostData {
    val document = Jsoup.parse(stream, "UTF-8", baseUri)
    val topicContainer = document.selectFirst(".topic-container")
    val author = topicContainer.selectFirst("a.user").text()
    val content = topicContainer.selectFirst(".topic-content").html()
    val dateString = topicContainer.selectFirst(".topic-info-date > time").text()
    val voteCountStr = topicContainer.selectFirst(".vote-count > span").text()
    val voteCount = parsePostVoteCount(voteCountStr)
    val contentInfo = ContentInfo(author, Html.fromHtml(content), dateString, voteCount)
    return PostData(contentInfo, parseComments(document))
}

private fun parseComments(document: Document): List<CommentNode> {
    // select all the top-level comment nodes
    val nodes = document.select("#comments > .comment-wrapper")
    if(nodes.isEmpty()) {
        return listOf()
    }
    return nodes.map { parseCommentWrapper(it) }
}

data class TreeWalkNode(val parent: TreeWalkNode?,
                        val element: Element,
                        var discovered: Boolean = false,
                        var children: MutableList<ContentInfo> = arrayListOf())

private fun parseCommentWrapperIterative(commentWrapper: Element) : CommentNode {
    // this implements a tree traversal depth-first algorithm
    // (with slight variation - adding children to parent nodes along the way)
    val stack : Stack<TreeWalkNode> = Stack()
    val rootNode = TreeWalkNode(null, commentWrapper)
    stack.push(rootNode)
    while(!stack.isEmpty()) {
        val node = stack.pop()
        if(!node.discovered) {
            node.discovered = true
            if(node.parent != null) {
                node.parent.children.add(parseComment(node.element))
            }

            val childElems = node.element.select(".comment-wrapper")
            for(i in childElems.size()-1 downTo 0) {
                stack.push(TreeWalkNode(node, childElems.get(i), discovered = false))
            }
        }
    }
    return toCommentNode(rootNode)
}

private fun toCommentNode(rootNode: TreeWalkNode) : CommentNode {
    // todo
}

fun parseCommentWrapper(commentWrapper: Element): CommentNode {
    val commentInfo = parseComment(commentWrapper.selectFirst(".comment"))
    val childCommentWrappers = commentWrapper.select(".comment-wrapper")
    val childNodes = if(!childCommentWrappers.isEmpty())
        childCommentWrappers.map { parseCommentWrapper(it) } else null
    return CommentNode(commentInfo, childNodes)
}

fun parseComment(commentElem: Element): ContentInfo {
    val author = commentElem.selectFirst(".comment-author").text()
    val content = commentElem.selectFirst(".comment-content > .text").html()
    val dateString = commentElem.selectFirst(".comment-date > time").text()
    val voteCountStr = commentElem.selectFirst(".vote-count").text()
    val voteCount = parsePostVoteCount(voteCountStr)
    return ContentInfo(author, content, dateString, voteCount)
}

