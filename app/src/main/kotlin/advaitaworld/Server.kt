package advaitaworld

import advaitaworld.net.AdvaitaWorldCookieHandler
import advaitaworld.net.Cache
import advaitaworld.net.MemoryCache
import advaitaworld.net.runRequest
import advaitaworld.parsing.*
import android.content.Context
import android.preference.PreferenceManager
import com.squareup.okhttp.*
import org.json.JSONObject
import org.jsoup.Jsoup
import rx.Observable
import timber.log.Timber
import java.io.BufferedReader
import java.io.Reader
import java.util.regex.Pattern

// FIXME move to some particular place which contains common app dependency providers?
private var server: Server? = null
class ServerProvider {
    fun get(context: Context): Server {
        if(server == null) {
            server = Server(context, MemoryCache())
            // FIXME remove this
            initMockData(context, server!!)
        }
        return server!!
    }

    fun get(context: Context, propertyMetadata: PropertyMetadata): Server {
        return get(context.getApplicationContext())
    }

}

public enum class Section(val nameResId: Int) {
    Popular : Section(R.string.section_main)
    Community : Section(R.string.section_blogs)
    Personal : Section(R.string.section_personal_blogs)
}

public class Server(context: Context, cache: Cache) {
    private val client = OkHttpClient()
    private val cache = cache

    init {
        if(client.getCookieHandler() == null) {
            Timber.d("installing a cookie handler")
            client.setCookieHandler(AdvaitaWorldCookieHandler(context))
        }
    }

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

    public fun isLoggedIn() : Boolean {
        // currently use a 'key' cookie availability as a flag that login was successfully performed
        // not sure if this is the most correct way to do this, will have to experiment and see
        // how it goes...
        val cookieHandler = client.getCookieHandler()!! as AdvaitaWorldCookieHandler
        return cookieHandler.getCookieValue(AdvaitaWorldCookieHandler.KEY_COOKIE_NAME) != null
    }

    /**
     * Performs a user login and returns a profile info of logged in user on success
     */
    public fun loginUser(userLogin: String, userPassword: String) : Observable<ProfileInfo> {
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
        //   - remember: "on"/"off"
        //   - securityLsKey (extracted from html) [required?]
        //   - [PHPSESSID cookie?]
        //
        // Login response contains:
        //   - json body of format
        //      { sUrlRedirect: "url"
        //        sMsgTitle: "" // title of error message (can be empty even if error)
        //        sMsg: "" // error message (usually filled in if error)
        //        bStateError: true|false }
        //   - cookie named "key" which will be required along with PHPSESSID cookie for
        //     proper recognition of user as being logged in

        // Login procedure is as follows:
        //   - retrieve a main page to ensure that cookies and securityLsKey are retrieved
        //   - execute a login request itself
        //   - retrieve a main page again and extract a logged in user name from it
        return runRequest(client, sectionUrl(Section.Popular))
            .map { extractSecurityKey(it.charStream()) }
            .flatMap { securityKey -> runRequest(client, loginRequest(userLogin, userPassword, securityKey)) }
            .map {
                val error = extractLoginErrorMaybe(it.string())
                if(error != null) {
                    Timber.e("login failed: $error")
                    throw RuntimeException(error)
                }
            }
            .flatMap { runRequest(client, sectionUrl(Section.Popular)) }
            .map { extractUserName(it.charStream()) }
            .flatMap { name -> getUserInfo(name) }
            .map { user -> ProfileInfo(user.name, userLogin, user.avatarUrl) }
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

    private fun loginRequest(userLogin: String, password: String, securityKey: String): Request {
        if(BuildConfig.DEBUG) {
            Timber.d("creating login request:\nuser=$userLogin,\nsecurityKey=$securityKey")
        }
        val postBody = FormEncodingBuilder()
            .add("login", userLogin)
            .add("password", password)
            .add("security_ls_key", securityKey)
            .add("remember", "on")
            .build()
        return Request.Builder()
            .url("http://advaitaworld.com/login/ajax-login/")
            .post(postBody)
            .build()
    }
}

private fun parseUserProfile(name: String, html: String): User {
    Timber.d("parsing profile for $name")
    val document = Jsoup.parse(html)
    val imgElem = document.select("div.profile-top > .avatar > img")
    val imgUrl = imgElem.get(0).attr("src")
    return User(name, imgUrl)
}

/**
 * Extracts a security key by parsing a html of main page
 */
private fun extractSecurityKey(content: Reader) : String {
    Timber.e("searching for security key")
    val pattern = Pattern.compile("LIVESTREET_SECURITY_KEY.*'(.+)'.*")
    return extractLine(content, pattern)
}

/**
 * Extracts a username of currently logged in user by parsing a html of main page.
 * Should be used on content fetched after a successful login
 */
private fun extractUserName(content: Reader) : String {
    Timber.e("searching for user name")
    val pattern = Pattern.compile("footer-list-header.+img.+avatar.+>(.+):.*</li>")
    return extractLine(content, pattern)
}

// Searches content line-by-line until pattern matches, returns result of group(1) on match
private fun extractLine(content: Reader, pattern: Pattern) : String {
    val reader = BufferedReader(content)
    try {
        // FIXME these two lines are here instead of just reader.lines().filter(...)
        // because of some weird type inference bug of kotlin compiler
        // see https://devnet.jetbrains.com/message/5540845, and remove them after it's fixed
        // upd: even worse, it crashes at runtime with LinkageError, rewriting in imperative style...
        var line = reader.readLine()
        while(line != null) {
            val matcher = pattern.matcher(line)
            if(matcher.find()) {
                return matcher.group(1)
            }
            line = reader.readLine()
        }
        throw RuntimeException("failed to find line matching pattern $pattern")
    } finally {
        reader.close()
    }
}

/**
 * Finds if login response had an error, returns a localized string description
 */
private fun extractLoginErrorMaybe(content: String) : String? {
    val jsonObj = JSONObject(content)
    if(jsonObj.getBoolean("bStateError")) {
        return jsonObj.getString("sMsg")
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
