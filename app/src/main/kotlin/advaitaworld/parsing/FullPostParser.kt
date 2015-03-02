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
import android.util.LongSparseArray
import org.jsoup.select.NodeVisitor
import org.jsoup.nodes.Node
import org.jsoup.select.NodeTraversor
import java.util.regex.Pattern

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
    return nodes.map { parseCommentsThreadIterative1(it) }
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

private class WorkNode(
        var id : Long,
        var parentId : Long,
        var contentInfo: ContentInfo,
        var path: LongArray,
        var children : MutableList<WorkNode>,
        // will be written when all children are parsed
        var resultNode: CommentNode?
)

private fun parseCommentsThreadIterative1(commentWrapper: Element) : CommentNode {
    val visitor = Visitor()
    NodeTraversor(visitor).traverse(commentWrapper)
    return visitor.rootNode!!
}

/**
 * Builds a tree of comments, by navigating through the DOM.
 * Not only it extracts comment info, but also builds parent-child relationships
 * between comments by analyzing data of "up-to-parent" actions which originally cause JS calls.
 * Comments are also given same IDs as they have on website.
 */
private class Visitor : NodeVisitor {
    // FIXME figure out if there is a way to hint about number of children?
    val workTree : LongSparseArray<WorkNode> = LongSparseArray()
    // reusable temporary var to avoid GC
    val pair = longArray(-1, -1)
    var rootNode : CommentNode? = null

    override fun head(domNode: Node, depth: Int) {
        if(!domNode.nodeName().equals("div") || !domNode.attr("class").startsWith("comment-wrapper")) {
            return
        }
        val content = parseComment(domNode as Element)
        val (id, parentId) = parseCommentIds(domNode as Element, pair)

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
        val deepCount = parsedChildren.fold(parsedChildren.size(), { (count, child) -> count + child.deepChildCount })
        val resultChildren = if(!parsedChildren.isEmpty()) parsedChildren else null
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
            path!!.set(sz, id)
        } else {
            path = longArray(id)
        }
        return path!!
    }
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
