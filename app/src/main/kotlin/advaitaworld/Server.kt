package advaitaworld

import rx.Observable
import com.squareup.okhttp.OkHttpClient
import timber.log.Timber
import com.squareup.okhttp.ResponseBody
import com.squareup.okhttp.MediaType
import org.jsoup.Jsoup
import android.text.Html
import java.util.regex.Pattern
import advaitaworld.db.User
import android.content.Context

public enum class Section {
    Popular
    Community
    Personal
}

public data class ContentInfo(val author: String,
                               val text: CharSequence,
                               val dateString: String,
                               val rating: String?)

public data class ShortPostInfo(
        val content: ContentInfo,
        val commentCount: String?)

public class Server {
    private val client = OkHttpClient()

    public fun getPosts(section: Section) : Observable<List<ShortPostInfo>> {
        Timber.d("getting posts for $section")
        return runMockableRequest(client, sectionUrl(section))
                .map({ parseHtml(it.string()) })
    }

    public fun getUserInfo(name: String) : Observable<User> {
        Timber.d("getting user info at: ${profileUrl(name)}")
        return runRequest(client, profileUrl(name))
                .map({ parseUserProfile(name, it.string()) })
    }

    // some other implementation of Server could use different urls
    fun sectionUrl(section: Section) : String {
        return when(section) {
            Section.Popular -> "http://advaitaworld.com"
            Section.Community -> "http://advaitaworld.com/blog/new"
            Section.Personal -> "http://advaitaworld.com/personal_blog/new"
            else -> throw RuntimeException("unknown section")
        }
    }

    // some other implementation of Server could use different urls
    fun profileUrl(name: String) : String {
        return "http://advaitaworld.com/profile/$name"
    }
}

private fun parseHtml(content: String): List<ShortPostInfo> {
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

// FIXME remove when mocking is not needed, use directly
private fun runMockableRequest(client: OkHttpClient, url: String) : Observable<ResponseBody> {
    if(MOCK_URL_DATA.containsKey(url)) {
        Timber.d("USING MOCK DATA for url $url")
        return Observable.just(ResponseBody.create(MediaType.parse("application/html"), MOCK_URL_DATA.get(url)))
    }
    return runRequest(client, url)
}

// FIXME remove
private val MOCK_URL_DATA: MutableMap<String, String> = hashMapOf()
public fun initMockData(context: Context, server: Server) {
    fun assetToString(fileName: String) : String{
        val stream = context.getAssets().open(fileName)
        return java.util.Scanner(stream).useDelimiter("\\A").next()
    }
    MOCK_URL_DATA.put(server.sectionUrl(Section.Popular),
            assetToString("main_test.html"))
}
