package advaitaworld.parsing

import rx.Observable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses a full post
 * @param stream a stream containing the html of the post page
 * @param baseUri a base uri to be used for resolving links
 */
public fun parseFullPost(stream: java.io.InputStream, baseUri: String) : PostData {
    val document = Jsoup.parse(stream, "UTF-8", baseUri)
    val topicContainer = document.selectFirst(".topic-container")
    val content = ContentInfo("no", "no", "date", null)
    return PostData(content, Observable.empty())
}

