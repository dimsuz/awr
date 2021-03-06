package advaitaworld.parsing

import android.content.Context
import android.text.Html
import android.util.LongSparseArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import timber.log.Timber
import java.util.Collections
import java.util.regex.Pattern

/**
 * Parses a full post
 * @param stream a stream containing the html of the post page
 * @param baseUri a base uri to be used for resolving links
 */
public fun parseFullPost(stream: java.io.InputStream, baseUri: String, mediaResolver: MediaResolver) : PostData {
    val document = Jsoup.parse(stream, "UTF-8", baseUri)
    val topicContainer = document.selectFirst(".topic-container")
    val title = topicContainer.selectFirst(".topic-title").text()
    val author = topicContainer.selectFirst("a.user").text()
    val content = topicContainer.selectFirst(".topic-content").html()
    val dateString = topicContainer.selectFirst(".topic-info-date > time").text()
    val voteCountStr = topicContainer.selectFirst(".vote-count > span").text()
    val voteCount = parsePostVoteCount(voteCountStr)
    val contentInfo = ContentInfo(author, parseHtmlContent(content, mediaResolver), dateString, voteCount)
    return PostData(title, contentInfo, parseComments(document, mediaResolver))
}

private fun parseComments(document: Document, mediaResolver: MediaResolver): List<CommentNode> {
    // select all the top-level comment nodes
    val nodes = document.select("#comments > .comment-wrapper")
    if(nodes.isEmpty()) {
        return listOf()
    }
    // this was used to warm up pools, leaving it here for some time, dunno if pools of WorkNodes
    // will be reintroduced
    val commentsCount = document.selectFirst("#count-comments").text().toInt()
    val threadCount = nodes.size()
    val avgCommentPerThreadCount = commentsCount/threadCount
    Timber.d("$threadCount threads contain $commentsCount comments, average comments per thread is $avgCommentPerThreadCount")
    return nodes.map { parseCommentsThreadIterative(it, mediaResolver, avgCommentPerThreadCount) }
}

private class WorkNode(
        var id : Long,
        var parentId : Long,
        var contentInfo: ContentInfo,
        var path: LongArray,
        var children : MutableList<WorkNode>,
        // will be written when all children are parsed
        var resultNode: CommentNode?
)

private fun parseCommentsThreadIterative(commentWrapper: Element, mediaResolver: MediaResolver, avgCommentPerThreadCount: Int) : CommentNode {
    val visitor = Visitor(mediaResolver, avgCommentPerThreadCount)
    NodeTraversor(visitor).traverse(commentWrapper)
    return visitor.rootNode!!
}

/**
 * Builds a tree of comments, by navigating through the DOM.
 * Not only it extracts comment info, but also builds parent-child relationships
 * between comments by analyzing data of "up-to-parent" actions which originally cause JS calls.
 * Comments are also given same IDs as they have on website.
 */
private class Visitor(mediaResolver: MediaResolver, avgCommentPerThreadCount: Int) : NodeVisitor {
    val mediaResolver = mediaResolver
    val workTree : LongSparseArray<WorkNode> = LongSparseArray(avgCommentPerThreadCount)
    // reusable temporary var to avoid GC
    val pair = longArrayOf(-1, -1)
    var rootNode : CommentNode? = null

    override fun head(domNode: Node, depth: Int) {
        if(!domNode.nodeName().equals("div") || !domNode.attr("class").startsWith("comment-wrapper")) {
            return
        }
        val content = parseComment(domNode as Element, mediaResolver)
        val (id, parentId) = parseCommentIds(domNode, pair)

        // top-level nodes can have id == -1
        val parentNode = if(parentId != -1L) workTree.get(parentId) else null
        val newNode = WorkNode(id, parentId, content, createPath(parentNode, id), arrayListOf(), null)
        workTree.put(id, newNode)
        if(parentNode != null) { parentNode.children.add(newNode) }
    }

    override fun tail(domNode: Node, depth: Int) {
        if(!domNode.nodeName().equals("div") || !domNode.attr("class").startsWith("comment-wrapper")) {
            return
        }
        // is called when all the node's children are visited => parsed, safe to create final values
        // for this node and its children
        val (id, parentId) = parseCommentIds(domNode as Element, pair)
        val node = workTree.get(id)!!
        val parsedChildren = node.children.map { it.resultNode!! }
        val deepCount = parsedChildren.fold(parsedChildren.size(), { count, child -> count + child.deepChildCount })
        val resultChildren = if(!parsedChildren.isEmpty()) parsedChildren else Collections.emptyList()
        node.resultNode = CommentNode(node.path, node.contentInfo, resultChildren, deepCount)

        if(parentId == -1L) {
            // got root node
            if(rootNode != null) { throw RuntimeException("root node is already assigned, expected a single root!") }
            rootNode = node.resultNode
        }
    }

    private fun createPath(parentNode: WorkNode?, id: Long) : LongArray {
        var path : LongArray?
        if(parentNode != null) {
            val sz = parentNode.path.size()
            path = parentNode.path.copyOf(sz + 1)
            path.set(sz, id)
        } else {
            path = longArrayOf(id)
        }
        return path
    }
}

private fun parseComment(commentElem: Element, mediaResolver: MediaResolver): ContentInfo {
    // NOTE: not using select(css) or selectFirst(css) here, because it proved to be very slow,
    // mainly because it uses String.split to match 'class' attributes which creates new instances
    // of Pattern on each invocation!
    val sectionElem = commentElem.getElementsByTag("section").first()
    val author = sectionElem.getElementsByAttributeValue("class", "comment-author").first()
            .child(0).text()
    val content = sectionElem.getElementsByAttributeValue("class", "comment-content").first()
            .child(0).html()
    val dateString = sectionElem.getElementsByTag("time").first().text()
    val voteCountStr = sectionElem.getElementsByAttributeValue("class", "vote-count").text()
    val voteCount = parsePostVoteCount(voteCountStr)
    return ContentInfo(author, parseHtmlContent(content, mediaResolver), dateString, voteCount)
}

private val commentIdPattern = Pattern.compile("\\((\\d+),(\\d+)\\)")

private fun parseCommentIds(node: Element, outPair: LongArray): LongArray {
    val sectionElem = node.getElementsByTag("section").first()
    val liElem = sectionElem.getElementsByAttributeValueEnding("class", "comment-parent").first()
    if(liElem == null) {
        // no parent id data, just extract comment id, from id attribute it is of the form comment_wrapper_id_NNNN
        outPair.set(0, node.id().substringAfterLast('_').toLong())
        outPair.set(1, -1)
    } else {
        val onClickText = liElem.child(0).attr("onclick")
        if (onClickText.isEmpty()) throw RuntimeException("failed to determine comment ids")
        val m = commentIdPattern.matcher(onClickText)
        if (!m.find()) throw RuntimeException("failed to determine comment ids, no pattern match")
        outPair.set(0, m.group(1).toLong())
        outPair.set(1, m.group(2).toLong())
    }
    return outPair
}
