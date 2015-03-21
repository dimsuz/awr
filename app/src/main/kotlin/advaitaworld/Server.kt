package advaitaworld

import rx.Observable
import com.squareup.okhttp.OkHttpClient
import timber.log.Timber
import com.squareup.okhttp.ResponseBody
import org.jsoup.Jsoup
import android.content.Context
import advaitaworld.parsing.parseFullPost
import advaitaworld.parsing.ShortPostInfo
import advaitaworld.parsing.PostData
import advaitaworld.parsing.parsePostFeed
import advaitaworld.parsing.User
import com.squareup.okhttp.MediaType

// FIXME move to some particular place which contains common app dependency providers?
private var server: Server? = null
class ServerProvider {
    fun get(context: Context): Server {
        if(server == null) {
            server = Server(MemoryCache())
            // FIXME remove this
            initMockData(context, server!!)
        }
        return server!!
    }

    fun get(context: Context, propertyMetadata: PropertyMetadata): Server {
        return get(context)
    }

}

public enum class Section {
    Popular
    Community
    Personal
}

public class Server(cache: Cache) {
    private val client = OkHttpClient()
    private val cache = cache

    public fun getPosts(section: Section) : Observable<List<ShortPostInfo>> {
        Timber.d("getting posts for $section")
        return runMockableRequest(client, sectionUrl(section))
                .map({ parsePostFeed(it.string()) })
    }

    public fun getFullPost(postId: String) : Observable<PostData> {
        Timber.d("getting full post: ${postUrl(postId)}")
        val logMsgCache = { (data: PostData) -> Timber.d("getting post $postId from cache") }
        return cache.getFullPost(postId).doOnNext(logMsgCache).onErrorResumeNext {
            Timber.d("getting post $postId from server")
            runMockableRequest(client, postUrl(postId))
                    .map { parseFullPost(it.byteStream(), baseUri = "http://advaitaworld.com/") }
                    .doOnNext { postData -> Timber.d("saving $postId to cache") }
                    .doOnNext { postData -> cache.saveFullPost(postId, postData) }
        }
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

    fun postUrl(postId: String) : String {
        return "http://advaitaworld.com/blog/$postId.html"
    }
}

private fun parseUserProfile(name: String, html: String): User {
    Timber.d("parsing profile for $name")
    val document = Jsoup.parse(html)
    val imgElem = document.select("div.profile-top > .avatar > img")
    val imgUrl = imgElem.get(0).attr("src")
    return User(name, imgUrl)
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
private val TEST_POST_ID: String = "40409"

public fun initMockData(context: Context, server: Server) {
    fun assetToString(fileName: String) : String{
        val stream = context.getAssets().open(fileName)
        return java.util.Scanner(stream).useDelimiter("\\A").next()
    }
    MOCK_URL_DATA.put(server.sectionUrl(Section.Popular),
            assetToString("main_test.html"))
    MOCK_URL_DATA.put(server.postUrl(TEST_POST_ID), assetToString("full_post_test_shorter.html"))
}
