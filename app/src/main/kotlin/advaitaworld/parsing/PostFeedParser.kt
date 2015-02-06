package advaitaworld.parsing

import org.jsoup.Jsoup
import android.text.Html
import java.util.regex.Pattern

fun parsePostFeed(content: String): List<ShortPostInfo> {
    val document = Jsoup.parse(content)
    val posts = document.select("article.topic")
    val parsedPosts = posts.map({ postElem ->
        val text = postElem.select(".topic-content").get(0).html()
        val author = postElem.select("a.user").get(0).text()
        val dateString = postElem.select(".topic-info-date > time").get(0).text()
        val commentElem = postElem.select(".topic-info-comments > a > span")
        val commentCount = if(!commentElem.isEmpty()) commentElem.get(0).text() else null
        val voteCountStr = postElem.select(".vote-count > span").get(0).text()
        val voteCount = parseVoteCount(voteCountStr)
        val contentInfo = ContentInfo(author, Html.fromHtml(text), dateString, voteCount)
        ShortPostInfo(contentInfo, commentCount)
    })
    return parsedPosts
}

private val votePattern = Pattern.compile("^[+-]\\d+")
private fun parseVoteCount(s: String): String? {
    val m = votePattern.matcher(s)
    if(m.matches()) {
        return s
    } else {
        return null
    }
}

