package advaitaworld.parsing

import org.jsoup.Jsoup
import android.text.Html
import java.util.regex.Pattern

fun parsePostFeed(content: String): List<ShortPostInfo> {
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
        val postLink = postElem.selectFirst("h2.topic-title > a").attr("href")
        val postId = parsePostLink(postLink)!!
        val contentInfo = ContentInfo(author, Html.fromHtml(text), dateString, voteCount)
        ShortPostInfo(postId, contentInfo, commentCount)
    })
    return parsedPosts
}

