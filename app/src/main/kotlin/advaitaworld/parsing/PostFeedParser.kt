package advaitaworld.parsing

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
        val contentInfo = ContentInfo(author, shortenForDisplay(parsedPost), dateString, voteCount)
        ShortPostInfo(postId, postTitle, contentInfo, commentCount)
    })
    return parsedPosts
}

private fun shortenForDisplay(text: CharSequence) : CharSequence {
    val MAX_LENGTH_CHARS = 1000
    // FIXME when text contains an image in the first MAX_LENGTH_CHARS, limit to somehting smaller than MAX_LENGTH_CHARS
    if(text.length() <= MAX_LENGTH_CHARS) {
        return text
    }

    // note that conversion to String will loose all span info. Anyway, it is not needed for limits calculations.
    // but if needed, will just use original text
    val limited = text.toString().substring(0, MAX_LENGTH_CHARS)
    // Not doing any handling of situation when cut happens to be in the middle of some span section:
    // turned out that subSequence already deals with it nicely, be updating the end of that span to
    // be the end of the new string
    return text.subSequence(0, findCutPoint(limited))
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

