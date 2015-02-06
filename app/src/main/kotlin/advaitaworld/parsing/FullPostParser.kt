package advaitaworld.parsing

import rx.Observable
import org.jsoup.Jsoup
import android.text.Html

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
    return PostData(contentInfo, Observable.empty())
}

