package advaitaworld

import advaitaworld.parsing.*
import android.content.Context
import com.squareup.okhttp.*
import org.jsoup.Jsoup
import rx.Observable
import timber.log.Timber
import java.net.CookieHandler
import java.net.URI

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

public enum class Section(val nameResId: Int) {
    Popular : Section(R.string.section_main)
    Community : Section(R.string.section_blogs)
    Personal : Section(R.string.section_personal_blogs)
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
        val logMsgCache = { data: PostData -> Timber.d("getting post $postId from cache") }
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

    public fun loginUser(userLogin: String, userPassword: String) : Observable<LoginInfo> {
        // Login mechanism uses a combination of two cookies:
        // - "PHPSESSID" which is a user session id. Usually gets set to expire after browser session.
        // - "key" which is a token which marks an authorized user. Usually gets set to expire after few days
        //
        // PHPSESSID gets sent by server in response on the initial page request, but also can
        // be extracted from the html code (from script section in 'head').
        //
        // "key" gets sent as the response to a login request, after successful authorization
        //
        // Login request needs:
        //   - login, password
        //   - securityLsKey (extracted from html) [required?]
        //   - [PHPSESSID cookie?]
        if(client.getCookieHandler() == null) {
            Timber.d("installing a cookie handler")
            client.setCookieHandler(AdvaitaWorldCookieHandler())
        }
        return runMockableRequest(client, sectionUrl(Section.Popular))
             // extract info with no user name, but other keys for login request
            .map { extractLoginInfo(it.charStream(), needUserInfo = false) }
            .flatMap({ loginInfo -> runRequest(client, loginRequest(userLogin, userPassword, loginInfo))})
             // extract again after login, this time with logged in user name
            .map { extractLoginInfo(it.charStream(), needUserInfo = true) }
    }

    // some other implementation of Server could use different urls
    fun sectionUrl(section: Section) : String {
        return when(section) {
            Section.Popular -> "http://advaitaworld.com"
            Section.Community -> "http://advaitaworld.com/blog/"
            Section.Personal -> "http://advaitaworld.com/personal_blog/"
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

    fun loginRequest(userLogin: String, password: String, loginInfo: LoginInfo): Request {
        val postBody = FormEncodingBuilder()
            .add("login", userLogin)
            .add("password", password)
            .add("security_ls_key", loginInfo.securityKey)
            .build()
        // TODO save SESSION_ID to cookie store
        return Request.Builder()
            .url("http://advaitaworld.com/login/ajax-login/")
            .post(postBody)
            .build()
    }
}

private class AdvaitaWorldCookieHandler : CookieHandler() {
    override fun put(uri: URI, responseHeaders: Map<String, List<String>>) {
        Timber.d("received cookies from $uri, headers: $responseHeaders")
    }

    override fun get(uri: URI, requestHeaders: Map<String, List<String>>): Map<String, List<String>> {
        Timber.d("got cookies request from $uri, headers: $requestHeaders")
        val resultHeaders : MutableMap<String, List<String>> = hashMapOf()
        Timber.d("returning headers: $requestHeaders")
        return resultHeaders
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
