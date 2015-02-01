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
import advaitaworld.db.User

public enum class Section {
    Popular
    Community
    Personal
}

public data class Post(val author: String,
                       val content: CharSequence,
                       val dateString: String,
                       val rating: String?,
                       val commentCount: String?)

public class Server {
    private val client = OkHttpClient()

    public fun getPosts(section: Section) : Observable<List<Post>> {
        Timber.d("getting posts for $section")
        return runRequest(client, sectionUrl(section))
                .map({ parseHtml(it.string()) })
    }

    public fun getUserInfo(name: String) : Observable<User> {
        Timber.d("getting user info at: ${profileUrl(name)}")
        return runRequest(client, profileUrl(name))
                .map({ parseUserProfile(name, it.string()) })
    }

    // some other implementation of Server could use different urls
    private fun sectionUrl(section: Section) : String {
        return when(section) {
            Section.Popular -> "http://advaitaworld.com"
            Section.Community -> "http://advaitaworld.com/blog/new"
            Section.Personal -> "http://advaitaworld.com/personal_blog/new"
            else -> throw RuntimeException("unknown section")
        }
    }

    // some other implementation of Server could use different urls
    private fun profileUrl(name: String) : String {
        return "http://advaitaworld.com/profile/$name"
    }
}

private fun parseHtml(content: String): List<Post> {
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
    return parsedPosts
}

private fun parseUserProfile(name: String, html: String): User {
    Timber.d("parsing profile for $name")
    val document = Jsoup.parse(html)
    val imgElem = document.select("div.profile-top > .avatar > img")
    val imgUrl = imgElem.get(0).attr("src")
    return User(name, imgUrl)
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
    if(MOCK_PAGE_HTML != null && !url.contains("profile")) {
        Timber.d("USING MOCK DATA for url $url")
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
