package advaitaworld.net

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import java.net.CookieHandler
import java.net.URI
import java.util.HashMap
import java.util.regex.Pattern

public class AdvaitaWorldCookieHandler(private val context: Context) : CookieHandler() {
    public companion object {
        public val SESSION_ID_COOKIE_NAME : String = "PHPSESSID"
        public val KEY_COOKIE_NAME  : String = "key"
    }

    /**
     * represents a cookie store which contains cookies served only during a user session
     * (app run time).
     * Key is cookie name, value is the value assigned to that name
     */
    private val sessionCookieStore : MutableMap<String, String> = hashMapOf()

    /**
     * Returns a preference-based cookie store which should be used for storing cookies
     * which need to survive across app restarts
     */
    private fun getStorePrefs() : SharedPreferences {
        return context.getSharedPreferences("cookie_store", Context.MODE_PRIVATE)
    }

    /**
     * Returns a cookie. Checks both session-only and permanent cookie stores
     */
    public fun getCookieValue(name: String) : String? {
        val cookieValue = sessionCookieStore.get(name)
        // either return or continue with a look up in stored cookies
        return if(cookieValue != null) cookieValue else getStorePrefs().getString(name, null)
    }

    // maps cookie names to whether to store them permanently across sessions or not
    private data class CookieConfig(val name: String, val storeAcrossSessions: Boolean)
    private val cookieConfigs = arrayOf(CookieConfig(SESSION_ID_COOKIE_NAME, false),
        CookieConfig(KEY_COOKIE_NAME, true))

    override fun put(uri: URI, responseHeaders: Map<String, List<String>>) {
        // Interested are two cookies:
        // PHPSESSID - session id. expires after session ended
        // key - which gets sent for 'remembering' user across sessions. expires after some interval,
        // returned by server.
        // first is saved to the in-memory store, second - in persistent store
        val cookies = responseHeaders.get("Set-Cookie")
        if(cookies == null) { return }

        Timber.d("received cookies from $uri:\n  ${cookies.join("\n  ")}")
        for(knownCookie in cookieConfigs) {
            val cookieValue = extractCookie(cookies, knownCookie.name)
            if(cookieValue != null) {
                Timber.d("  saving '${knownCookie.name}' to a ${if(knownCookie.storeAcrossSessions) "permanent" else "temporary"} cookie store")
                if(knownCookie.storeAcrossSessions) {
                    getStorePrefs().edit().putString(knownCookie.name, cookieValue).apply()
                } else {
                    sessionCookieStore.put(knownCookie.name, cookieValue)
                }
            }
        }
    }

    override fun get(uri: URI, requestHeaders: Map<String, List<String>>): Map<String, List<String>> {
        Timber.d("got cookies request from $uri")
        val resultHeaders : MutableMap<String, List<String>> = hashMapOf()
        val cookies = getAllCookies().map { entry -> "${entry.getKey()}=${entry.getValue()}" }
        for(c in cookies) {
            resultHeaders.put("Cookie", cookies)
        }
        Timber.d("returning cookies: $resultHeaders")
        return resultHeaders
    }

    /**
     * Returns both in-memory and stored cookies in a single map
     */
    private fun getAllCookies() : Map<String, String> {
        val allCookies = HashMap(sessionCookieStore)
        val prefs = getStorePrefs()
        val stored = prefs.getAll()
        for(c in stored) {
            allCookies.put(c.getKey(), prefs.getString(c.getKey(), ""))
        }
        return allCookies
    }

    private fun extractCookie(cookies: List<String>, name: String) : String? {
        val pattern = Pattern.compile("$name=(.+?);")
        // need to go backwards because it turns out server can return several cookies with same
        // key but different value - need to extract one that appears last
        // (example: happens for cookie "key": key=deleted,...,key=uuid-value)
        for(i in cookies.size()-1 downTo 0) {
            val matcher = pattern.matcher(cookies.get(i))
            if(matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
}
