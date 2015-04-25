package advaitaworld.net

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import java.net.CookieHandler
import java.net.URI
import java.util.regex.Pattern

public class AdvaitaWorldCookieHandler(private val context: Context) : CookieHandler() {
    private val SESSION_ID_COOKIE_NAME = "PHPSESSID"

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

    override fun put(uri: URI, responseHeaders: Map<String, List<String>>) {
        val cookies = responseHeaders.get("Set-Cookie")
        if(cookies != null) {
            Timber.d("received cookies from $uri:\n${cookies.join("\n")}")
            val sessionId = extractSessionId(cookies)
            if(sessionId != null) {
                Timber.d("saving session id '$sessionId' to cookie store")
                // NOTE sessionId cookie has no Expire param set, so not saving it
                // to prefs so it will expire at the session end (aka app launch end)
                sessionCookieStore.put(SESSION_ID_COOKIE_NAME, sessionId)
            }
        }
    }

    override fun get(uri: URI, requestHeaders: Map<String, List<String>>): Map<String, List<String>> {
        Timber.d("got cookies request from $uri")
        val resultHeaders : MutableMap<String, List<String>> = hashMapOf()
        val cookies = sessionCookieStore.map { entry -> "${entry.getKey()}=${entry.getValue()}" }
        for(c in sessionCookieStore) {
            resultHeaders.put("Cookie", cookies)
        }
        Timber.d("returning cookies: $resultHeaders")
        return resultHeaders
    }

    private fun extractSessionId(cookies : List<String>) : String? {
        val pattern = Pattern.compile("$SESSION_ID_COOKIE_NAME=(.+?);")
        for(c in cookies) {
            val matcher = pattern.matcher(c)
            if(matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
}
