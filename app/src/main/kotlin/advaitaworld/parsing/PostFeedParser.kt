package advaitaworld.parsing

import android.text.Spanned
import android.text.style.ClickableSpan
import org.jsoup.Jsoup

fun parsePostFeed(content: String, mediaResolver: MediaResolver): List<ShortPostInfo> {
    val document = Jsoup.parse(content)
    val posts = document.select("article.topic")
    val parsedPosts = posts.map({ postElem ->
        val text = postElem.selectFirst(".topic-content").html()
        val author = postElem.selectFirst("a.user").text()
        val dateString = postElem.selectFirst(".topic-info-date > time").text()
        val commentElem = postElem.select(".topic-info-comments > a > span")
        val commentCount = if(!commentElem.isEmpty()) commentElem.get(0).text() else null
        val voteCountStr = postElem.selectFirst(".vote-count > span").text()
        val voteCount = parsePostVoteCount(voteCountStr)
        val postTitleElem = postElem.selectFirst("h2.topic-title > a")
        val postTitle = postTitleElem.text()
        val postLink = postTitleElem.attr("href")
        val postId = parsePostLink(postLink)!!
        val parsedPost = parseHtmlContent(text, mediaResolver)
        val shortenedPost = shortenForDisplay(parsedPost)
        val contentInfo = ContentInfo(author, shortenedPost, dateString, voteCount)
        ShortPostInfo(postId, postTitle, contentInfo,
            isExpandable = parsedPost.length() != shortenedPost.length(),
            commentCount = commentCount)
    })
    return parsedPosts
}

private fun shortenForDisplay(text: CharSequence) : CharSequence {
    val maxLength = getMaxLength(text)
    if(text.length() <= maxLength) {
        return text
    }

    // note that conversion to String will loose all span info. Anyway, it is not needed for limits calculations.
    // but if needed, will just use original text
    val limited = text.toString().substring(0, maxLength)
    // Not doing any handling of situation when cut happens to be in the middle of some span section:
    // turned out that subSequence already deals with it nicely, be updating the end of that span to
    // be the end of the new string
    return text.subSequence(0, findCutPoint(limited))
}

// FIXME when text contains an image in the first MAX_LENGTH_CHARS, limit to somehting smaller than MAX_LENGTH_CHARS
private fun getMaxLength(text: CharSequence): Int {
    val defaultMaxLength = 1000
    // Sometimes user can insert 'cut' right in the beginning of the post, resulting in 'Read more'
    // link appearing earlier than a defaultMaxLength used here. In this case the we will limit
    // the post summary even more, so that it excludes this ugly (in app world) link
    val readMoreStartPos = getReadMoreLinkPos(text, defaultMaxLength)
    return if(readMoreStartPos == -1) defaultMaxLength else readMoreStartPos
}

/**
 * Returns a start position of 'Read more ->' link or -1 if it is not found in range [0, endIndex]
 */
private fun getReadMoreLinkPos(text: CharSequence, endIndex: Int) : Int {
    // Find a clickable span with predefined text used by AW
    // (there can be other valid links which is not of concern here)
    val readMoreText = "Читать дальше"
    val spanned = text as Spanned
    val clickSpans = spanned.getSpans(0, endIndex, javaClass<ClickableSpan>())
    for(span in clickSpans) {
        val s = spanned.getSpanStart(span)
        val e = spanned.getSpanEnd(span)
        val spanContents = text.subSequence(s, e)
        if(spanContents.toString().contains(readMoreText)) {
            return s
        }
    }
    return -1
}

private fun findCutPoint(s: String) : Int {
    // prefer to limit on last sentense rather than chop between words...
    // try in order: dot, unicode '...', comma, etc, space
    val seps = ".\u2026!?;:,\n "
    var cutIdx = -1
    for(c in seps) {
        cutIdx = s.lastIndexOf(c)
        if(cutIdx != -1) break
    }
    // if cutIdx == -1 here: who in their right mind would submit a text without any delimeters? oh, well...
    return if(cutIdx != -1) Math.min(cutIdx+1, s.length()) else s.length()
}

