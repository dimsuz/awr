package advaitaworld

import rx.Observable
import com.squareup.okhttp.OkHttpClient
import advaitaworld.util.runOnce
import com.squareup.okhttp.Request
import java.io.IOException
import java.io.InputStream
import timber.log.Timber
import com.squareup.okhttp.ResponseBody
import com.squareup.okhttp.MediaType
import org.jsoup.Jsoup
import android.text.Html
import java.util.regex.Pattern

public enum class Section {
    Popular
    Community
    Personal
}

public data class Post(val author: CharSequence,
                       val content: CharSequence,
                       val dateString: String,
                       val rating: String?,
                       val commentCount: String?)

public class Server {
    private val client = OkHttpClient()

    public fun getPosts(section: Section) : Observable<List<Post>> {
        Timber.d("getting posts for $section")
        return runRequest(client, sectionUrl(section))
                .flatMap({ parseHtml(it.string()) })
    }

    // some other implementation of server could use different urls
    private fun sectionUrl(section: Section) : String {
        return when(section) {
            Section.Popular -> "http://advaitaworld.com"
            Section.Community -> "http://advaitaworld.com/blog/new"
            Section.Personal -> "http://advaitaworld.com/personal_blog/new"
            else -> throw RuntimeException("unknown section")
        }
    }
}

private fun parseHtml(content: String): Observable<List<Post>> {
    return Observable.create({ subscriber ->
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
            Post(author, Html.fromHtml(text), dateString, voteCount, commentCount)
        })
        if(!subscriber.isUnsubscribed()) {
            subscriber.onNext(parsedPosts)
            subscriber.onCompleted()
        }
    })
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

// FIXME put in some common place, this is a generic method
private fun runRequest(client: OkHttpClient, url: String) : Observable<ResponseBody> {
    if(MOCK_PAGE_HTML != null) {
        Timber.d("USING MOCK DATA")
        val scanner = java.util.Scanner(MOCK_PAGE_HTML).useDelimiter("\\A")
        val content = if(scanner.hasNext()) scanner.next() else ""
        return Observable.just(ResponseBody.create(MediaType.parse("application/html"), content))
    }
    return runOnce {
        Timber.d("starting request for url $url")
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if(!response.isSuccessful()) {
            throw IOException("unexpected http code: ${response.code()}")
        }
        Timber.d("got successful response for $url")
        response.body()
    }
}

// FIXME remove
public var MOCK_PAGE_HTML: InputStream? = null
