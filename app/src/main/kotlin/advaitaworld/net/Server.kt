package advaitaworld.net

import advaitaworld.BuildConfig
import advaitaworld.R
import advaitaworld.parsing.*
import android.content.Context
import com.squareup.okhttp.*
import org.json.JSONObject
import org.jsoup.Jsoup
import rx.Observable
import timber.log.Timber
import java.io.BufferedReader
import java.io.Reader
import java.util.Scanner
import java.util.regex.Pattern

public enum class Section(val nameResId: Int) {
    Popular : Section(advaitaworld.R.string.section_main)
    Community : Section(advaitaworld.R.string.section_blogs)
    Personal : Section(advaitaworld.R.string.section_personal_blogs)
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
            .flatMap { securityKey ->
                runRequest(client, loginRequest(userLogin, userPassword, securityKey))
                    .map { responseBody -> Pair(responseBody, securityKey) }
            }
            .map { bodyKeyPair ->
                val error = extractLoginErrorMaybe(bodyKeyPair.first.string())
                if(error != null) {
                    Timber.e("login failed: $error")
                    throw RuntimeException(error)
                }
                bodyKeyPair
            }
            // request a page again, this time user will be logged in, his name should appear
            .flatMap { bodyKeyPair ->
                runRequest(client, sectionUrl(Section.Popular))
                    .map { responseBody -> Pair(responseBody, bodyKeyPair.second) }
            }
            // start assembling finalized ProfileInfo: get user name. login, securityKey are known
            .map { bodyKeyPair ->
                val userName = extractUserName(bodyKeyPair.first.charStream())
                ProfileInfo(userName, userLogin, "", bodyKeyPair.second)
            }
            // finally a last piece: avatar url
            .flatMap { profileInfo ->
                getUserInfo(profileInfo.name)
                    .map { ProfileInfo(profileInfo.name, profileInfo.email, it.avatarUrl, profileInfo.securityKey) }
            }
    }

    /**
     * Logs out currently signed in user
     */
    public fun logoutUser(profileInfo: ProfileInfo) : Observable<Unit> {
        val url = "http://advaitaworld.com/login/exit/?security_ls_key=${profileInfo.securityKey}"
        return runRequest(client, url).map {}
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
        if(advaitaworld.BuildConfig.DEBUG) {
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