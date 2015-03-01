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
import java.util.Arrays

private var nextCommentNodeId: Long = 0

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
        nodePool.release(TreeWalkNode(path = longArray(), element = null, parsedChildren = null, deepChildCount = 0))
    }
}

data class TreeWalkNode(var path: LongArray,
                        var element: Element?,
                        var parsedChildren: MutableList<CommentNode>?,
                        var deepChildCount: Int) {
    fun init(path: LongArray, element: Element?, parsedChildren: MutableList<CommentNode>?, deepChildCount: Int)  {
        this.path = path
        this.element = element; this.parsedChildren = parsedChildren; this.deepChildCount = deepChildCount
    }
    fun level() : Int { return path.size() - 1 }
}

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

    val rootNode = getNodeFromPool(nodePool, longArray(nextCommentNodeId++), commentWrapper)

    var maxLevel = 0
    queue.addLast(rootNode)
    while(!queue.isEmpty()) {
        val node = queue.removeLast()
        walkPath.add(node)
        maxLevel = if(maxLevel < node.level()) node.level() else maxLevel

        val childElems = node.element!!.children()
        for(e in childElems) {
            if(e.hasClass("comment-wrapper")) {
                val path = Arrays.copyOf(node.path, node.path.size() + 1)
                path.set(path.lastIndex, nextCommentNodeId++)
                val newNode = getNodeFromPool(nodePool, path, element = e)
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

private fun getNodeFromPool(nodePool: Pool<TreeWalkNode>, path: LongArray, element: Element?): TreeWalkNode {
    val newNode = nodePool.acquire() ?: TreeWalkNode(path, element, null, 0)
    // if acquired from pool, initialize
    if(newNode.path.isEmpty()) { newNode.init(path, element, null, 0) }
    return newNode
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
            if (node.level() < currentLevel && !levelContent.isEmpty()) {
                // iterated to the parent element of accumulated ones
                node.parsedChildren = ArrayList(levelContent)
                node.deepChildCount = levelContent.fold(levelContent.size(), { (sum,node) -> sum + node.deepChildCount } )
                levelContent.clear()
            } else if(node.level() == currentLevel) {
                iterator.remove()
                levelContent.add(CommentNode(node.path, parseComment(node.element!!), node.parsedChildren, node.deepChildCount))
                // release to pool by setting the path to special value which must not exists in real world
                node.path = longArray()
                nodePool.release(node)
            } // else skip...
        }
        currentLevel = currentLevel - 1
    }
    // we should end up with exactly root node in the list
    return levelContent.single()
}

private fun parseComment(commentElem: Element): ContentInfo {
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
    return ContentInfo(author, content, dateString, voteCount)
}
