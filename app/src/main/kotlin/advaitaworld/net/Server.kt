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
    Popular(advaitaworld.R.string.section_main),
    Community(advaitaworld.R.string.section_blogs),
    Personal(advaitaworld.R.string.section_personal_blogs)
}

public class Server(context: Context, cache: Cache) {
    private val client = OkHttpClient()
    private val cache = cache
    private val parseAssistant = AwParseAssistant()
    val urls = parseAssistant.urlProvider()

    init {
        if(client.getCookieHandler() == null) {
            Timber.d("installing a cookie handler")
            client.setCookieHandler(LiveStreetCookieHandler(context))
        }
    }

    public fun getPosts(section: Section, mediaResolver: MediaResolver) : Observable<List<ShortPostInfo>> {
        Timber.d("getting posts for $section")
        return runMockableRequest(client, urls.sectionUrl(section))
                .map({ parseAssistant.parsePostFeed(it.string(), mediaResolver) })
    }

    public fun getFullPost(postId: String, mediaResolver: MediaResolver) : Observable<PostData> {
        Timber.d("getting full post: ${urls.postUrl(postId)}")
        val requestObservable = runMockableRequest(client, urls.postUrl(postId))
            .map { parseFullPost(it.byteStream(), baseUri = urls.baseUrl, mediaResolver = mediaResolver) }
            .doOnNext { postData -> Timber.d("saving $postId to cache") }
            .doOnNext { postData -> cache.saveFullPost(postId, postData) }

        return cache.getFullPost(postId)
            .doOnNext { data: PostData -> Timber.d("getting post $postId from cache") }
            .onErrorResumeNext(requestObservable)
    }

    public fun getUserInfo(name: String) : Observable<User> {
        Timber.d("getting user info at: ${urls.profileUrl(name)}")
        return runRequest(client, urls.profileUrl(name))
                .map({ parseAssistant.parseUserProfile(name, it.string()) })
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
        return runRequest(client, urls.sectionUrl(Section.Popular))
            .map { parseAssistant.extractSecurityKey(it.charStream()) }
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
                runRequest(client, urls.sectionUrl(Section.Popular))
                    .map { responseBody -> Pair(responseBody, bodyKeyPair.second) }
            }
            // start assembling finalized ProfileInfo: get user name. login, securityKey are known
            .map { bodyKeyPair ->
                val userName = parseAssistant.extractLoggedUserName(bodyKeyPair.first.charStream())
                ProfileInfo(userName, userLogin, "", bodyKeyPair.second)
            }
            // finally a last piece: avatar url
            .flatMap { profileInfo ->
                getUserInfo(profileInfo.name)
                    .map { ProfileInfo(profileInfo.name, profileInfo.email, it.avatarUrl, profileInfo.securityKey) }
            }
            .doOnNext { cache.clear() }
            .doOnError {
                Timber.e("user login is failing, clearing auth cookies in case they were already saved...")
                val cookieHandler = client.getCookieHandler() as LiveStreetCookieHandler
                cookieHandler.clearAuthCookies()
            }
    }

    /**
     * Logs out currently signed in user
     */
    public fun logoutUser(profileInfo: ProfileInfo) : Observable<Unit> {
        return runRequest(client, urls.logoutUrl(profileInfo.securityKey)).map {}
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
            .url(urls.loginUrl())
            .post(postBody)
            .build()
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
    MOCK_URL_DATA.put(server.urls.sectionUrl(Section.Popular),
            assetToString("main_test.html"))
    MOCK_URL_DATA.put(server.urls.postUrl(TEST_POST_ID), assetToString("full_post_test_shorter.html"))
}
