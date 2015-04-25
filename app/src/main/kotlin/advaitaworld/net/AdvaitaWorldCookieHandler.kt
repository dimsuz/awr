package advaitaworld.net

import timber.log.Timber
import java.net.CookieHandler
import java.net.URI

public class AdvaitaWorldCookieHandler : CookieHandler() {
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