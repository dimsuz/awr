package advaitaworld.net

import advaitaworld.BuildConfig
import advaitaworld.parsing.ParseAssistant
import com.squareup.okhttp.OkHttpClient
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.Collections

public class LiveStreetSession(private val parseAssitant: ParseAssistant, private val cookieChanges: Observable<CookieInfo>) {
    // NOTE: using a concurrent hash map because session changes are inserted on the main thread,
    // while getSecurityKey() can be called on any other thread, need to synchronize reads
    private val sessionIdKeyMap : MutableMap<String, String> = Collections.synchronizedMap(hashMapOf())

    init {
        cookieChanges.filter { it.name == LiveStreetCookieHandler.SESSION_ID_COOKIE_NAME }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                val prevId = if(sessionIdKeyMap.isNotEmpty()) sessionIdKeyMap.keySet().first() else "null"
                Timber.d("detected a php session id change: ${prevId}} => ${it.value}")
                sessionIdKeyMap.clear()
                sessionIdKeyMap.put(it.value, "")
            })
    }

    /**
     * Returns an observable which will emit an up-to-date session info: id and securityKey
     * This info can be used to send a correct requests to server without it suspecting a CSRF attack
     */
    public fun getSessionInfo(client: OkHttpClient) : Observable<SessionInfo> {
        val sessionInfo = tryGetCurrentFullInfo(sessionIdKeyMap)
        if(sessionInfo != null) {
            Timber.d("returning a current valid session info: $sessionInfo")
            return Observable.just(sessionInfo)
        }

        // fast route did not work, session changed, need to extract a new key

        // some generic request to the website, response is expected to contain security key in html source
        // which then will be extracted
        val requestObservable = runRequest(client, parseAssitant.urlProvider().baseUrl)
        if(sessionIdKeyMap.isEmpty()) {
            // map is empty means that no session id cookies were saved yet (can happen on app start),
            // need to run some request first so that cookies get populated
            // and after that this call is repeated hoping that map will be appropriately filled with sessionId then
            Timber.d("session info map is empty, trying to run request to fill cookies first...")
            return requestObservable.flatMap {
                Timber.d("'cookie-filler-request' finished, retrying getting full session info...")
                getSessionInfo(client)
            }
        }

        val sessionId = sessionIdKeyMap.keySet().first()
        return requestObservable
            .map { body ->
                SessionInfo(sessionId, securityKey = parseAssitant.extractSecurityKey(body.charStream()))
            }
            .doOnNext { sessionInfo ->
                Timber.d("saving new sessionInfo=$sessionInfo")
                sessionIdKeyMap.put(sessionInfo.sessionId, sessionInfo.securityKey)
            }
    }

    /**
     * Returns a sessionId which should be used with all outgoing request to website, if needed.
     * If you need both securityKey and sessionId, better call getSessionInfo() which will get both in one request
     */
    public fun getSessionId(client: OkHttpClient) : Observable<String> {
        if(sessionIdKeyMap.isNotEmpty()) {
            return Observable.just(sessionIdKeyMap.keySet().first())
        } else {
            // retrieve session info, that will ensure that sessionIdKeyMap correctly populated
            // then retry
            return getSessionInfo(client).flatMap { getSessionId(client) }
        }
    }
}

public data class SessionInfo(val sessionId : String, val securityKey : String)

/**
 * Returns either a session info with **both** id and key filled or null if some info is missing
 */
private fun tryGetCurrentFullInfo(sessionIdKeyMap: Map<String, String>): SessionInfo? {
    // this map can be empty on app start which indicates the need to re-fetch session id
    if(sessionIdKeyMap.isEmpty()) {
        return null
    }
    // if not empty, sessionIdKeyMap always contains a single entry with current session and associated key
    // see if it can be returned right away
    if(BuildConfig.DEBUG) {
        if(sessionIdKeyMap.size() > 1) throw RuntimeException("sessionIdKeyMap is supposed to have single entry!")
    }
    val (sessionId,securityKey) = sessionIdKeyMap.iterator().next()

    return if(securityKey.isNotEmpty()) SessionInfo(sessionId,securityKey) else null
}
