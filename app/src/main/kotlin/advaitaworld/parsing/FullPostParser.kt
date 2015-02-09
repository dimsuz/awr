package advaitaworld.parsing

import org.jsoup.Jsoup
import android.text.Html
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.ArrayDeque
import java.util.ArrayList
import android.support.v4.util.Pools.SimplePool
import timber.log.Timber
import android.support.v4.util.Pools.Pool

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
    val commentsCount = document.selectFirst("#count-comments").text().toInt()
    val threadCount = nodes.size()
    val avgCommentPerThreadCount = commentsCount/threadCount
    Timber.d("$threadCount threads contain $commentsCount comments, average comments per thread is $avgCommentPerThreadCount")
    val dequePool = SimplePool<ArrayDeque<TreeWalkNode>>(3)
    val nodePool = SimplePool<TreeWalkNode>(avgCommentPerThreadCount)
    warmUpPools(dequePool, nodePool, avgCommentPerThreadCount)
    return nodes.map { parseCommentsThreadIterative(it, dequePool, nodePool) }
}

private fun warmUpPools(dequePool: Pool<ArrayDeque<TreeWalkNode>>, nodePool: Pool<TreeWalkNode>, avgCommentPerThreadCount: Int) {
    // two deques - one for 'stack', one for walk path
    dequePool.release(ArrayDeque<TreeWalkNode>(avgCommentPerThreadCount))
    dequePool.release(ArrayDeque<TreeWalkNode>(avgCommentPerThreadCount))
    for(i in (1..avgCommentPerThreadCount)) {
        nodePool.release(TreeWalkNode(element = null, level = -1, parsedChildren = null))
    }
}

data class TreeWalkNode(var element: Element?, var level: Int, var parsedChildren: MutableList<CommentNode>?)

// Parsing happens in two stages: first the whole comment tree is traversed in the depth-first order,
// it builds the walking path of the form (P-parent, C-Child): P C C C P C C P
// After path is built, it is used to reconstruct the tree, bottom up, by
// accumulating the child nodes until the parent node of higher level will be met.
// The walk path is constructed so that parent nodes will be immediately followed by their direct children
// (when looking from left to right)
private fun parseCommentsThreadIterative(commentWrapper: Element,
                                         dequePool: SimplePool<ArrayDeque<TreeWalkNode>>,
                                         nodePool: SimplePool<TreeWalkNode>) : CommentNode {
    // expecting pools to be warmed up
    val queue : ArrayDeque<TreeWalkNode> = dequePool.acquire()!!
    val walkPath: ArrayDeque<TreeWalkNode> = dequePool.acquire()!!

    val rootNode = nodePool.acquire() ?: TreeWalkNode(commentWrapper, 0, null)
    // if acquired from pool, initialize
    if(rootNode.level == -1) { rootNode.element = commentWrapper; rootNode.level = 0; rootNode.parsedChildren = null }

    var maxLevel = 0
    queue.addLast(rootNode)
    while(!queue.isEmpty()) {
        val node = queue.removeLast()
        walkPath.add(node)
        maxLevel = if(maxLevel < node.level) node.level else maxLevel

        val childElems = node.element!!.children()
        for(e in childElems) {
            if(e.hasClass("comment-wrapper")) {
                val l = node.level + 1
                val newNode = nodePool.acquire() ?: TreeWalkNode(e, l, null)
                // if acquired from pool, initialize
                if(newNode.level == -1) { newNode.element = e; newNode.level = l; newNode.parsedChildren = null }
                queue.addLast(newNode)
            }
        }
    }

    val result = buildFromWalkPath(walkPath, maxLevel, nodePool)
    // after building the nodes tree, release all we can to pools to avoid recreating a lot of objects while parsing
    // next comment thread
    walkPath.clear(); queue.clear()
    dequePool.release(walkPath); dequePool.release(queue)
    return result
}

// See detailed description of the whole process above
private fun buildFromWalkPath(walkPath: ArrayDeque<TreeWalkNode>, maxLevel: Int, nodePool: Pool<TreeWalkNode>): CommentNode {
    if(walkPath.isEmpty()) {
        throw RuntimeException("no elements")
    }
    //Timber.d("max level is $maxLevel")
    var currentLevel = maxLevel
    val levelContent: MutableList<CommentNode> = arrayListOf()
    while(!walkPath.isEmpty()) {
        val iterator = walkPath.descendingIterator()
        while (iterator.hasNext()) {
            val node = iterator.next()
            if (node.level < currentLevel && !levelContent.isEmpty()) {
                // iterated to the parent element of accumulated ones
                node.parsedChildren = ArrayList(levelContent)
                levelContent.clear()
            } else if(node.level == currentLevel) {
                iterator.remove()
                levelContent.add(CommentNode(parseComment(node.element!!), node.parsedChildren))
                // release to pool by setting the level to special value
                node.level = -1
                nodePool.release(node)
            } // else skip...
        }
        currentLevel = currentLevel - 1
    }
    // we should end up with exactly root node in the list
    return levelContent.single()
}

private fun parseComment(commentElem: Element): ContentInfo {
    val author = commentElem.selectFirst(".comment-author").text()
    val content = commentElem.selectFirst(".comment-content > .text").html()
    val dateString = commentElem.selectFirst(".comment-date > time").text()
    val voteCountStr = commentElem.selectFirst(".vote-count").text()
    val voteCount = parsePostVoteCount(voteCountStr)
    return ContentInfo(author, content, dateString, voteCount)
}
