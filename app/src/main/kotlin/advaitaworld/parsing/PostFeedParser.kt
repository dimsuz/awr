package advaitaworld.parsing

import android.text.Html
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.BufferedReader
import java.io.Reader
import java.util.regex.MatchResult
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
        val postTitleElem = postElem.selectFirst("h2.topic-title > a")
        val postTitle = postTitleElem.text()
        val postLink = postTitleElem.attr("href")
        val postId = parsePostLink(postLink)!!
        val contentInfo = ContentInfo(author, Html.fromHtml(text), dateString, voteCount)
        ShortPostInfo(postId, postTitle, contentInfo, commentCount)
    })
    return parsedPosts
}

fun extractLoginInfo(content: Reader, needUserInfo: Boolean) : LoginInfo {
    val reader = BufferedReader(content)
    try {
        val patterns = createMatchPatterns(needUserInfo)
        val matchResults: MutableMap<String, MatchResult> = hashMapOf()
        reader.lines()
            .takeWhile { matchResults.size() != patterns.size() }
            .forEach { line ->
                Timber.d("analyzing line $line")
                for(e in patterns) {
                    val matcher = e.getValue().matcher(line)
                    if(matcher.find()) {
                        // found one, save it,
                        matchResults.put(e.getKey(), matcher.toMatchResult())
                        // this line could contain some other pattern, so next for-loop iteration
                        // will continue from the last spot
                    }
                }
            }
        return processMatchResults(matchResults)
    } finally {
        reader.close()
    }
}

private fun createMatchPatterns(needUserInfo: Boolean) : Map<String, Pattern> {
    val patterns = hashMapOf(
        "sessionId" to Pattern.compile(""),
        "securityKey" to Pattern.compile(""))
    if(needUserInfo) patterns.put("userName", Pattern.compile(""))
    return patterns
}

private fun processMatchResults(matchResults: Map<String, MatchResult>): LoginInfo {
    throw UnsupportedOperationException()
}
