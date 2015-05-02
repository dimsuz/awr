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

    override fun put(uri: URI, responseHeaders: Map<String, List<String>>) {
        // Interested are two cookies:
        // PHPSESSID - session id. expires after session ended
        // key - which gets sent for 'remembering' user across sessions. expires after some interval,
        // returned by server.
        // first is saved to the in-memory store, second - in persistent store
        val cookies = responseHeaders.get("Set-Cookie")
        if(cookies != null) {
            Timber.d("received cookies from $uri:\n  ${cookies.join("\n  ")}")
            val sessionId = extractCookie(cookies, SESSION_ID_COOKIE_NAME)
            if(sessionId != null) {
                Timber.d("  saving session id '$sessionId' to cookie store")
                // NOTE sessionId cookie has no Expire param set, so not saving it
                // to prefs so it will expire at the session end (aka app launch end)
                sessionCookieStore.put(SESSION_ID_COOKIE_NAME, sessionId)
            }
            val key = extractCookie(cookies, KEY_COOKIE_NAME)
            if(key != null) {
                Timber.d("  saving key '$key' to a permanent cookie store")
                // NOTE sessionId cookie has no Expire param set, so not saving it
                // to prefs so it will expire at the session end (aka app launch end)
                getStorePrefs().edit().putString(KEY_COOKIE_NAME, key).apply()
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

    private fun extractCookie(cookies : List<String>, name: String) : String? {
        val pattern = Pattern.compile("$name=(.+?);")
        for(c in cookies) {
            val matcher = pattern.matcher(c)
            if(matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
}
