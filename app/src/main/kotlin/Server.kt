package com.advaitaworld.app

import rx.Observable
import com.squareup.okhttp.OkHttpClient
import com.advaitaworld.app.util.runOnce
import com.squareup.okhttp.Request
import java.io.IOException
import java.io.InputStream
import org.xml.sax.helpers.XMLReaderFactory
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import timber.log.Timber

public enum class Section {
    Popular
    Community
    Personal
}

public data class Post(val content: CharSequence)

public class Server {
    private val client = OkHttpClient()

    public fun getPosts(section: Section) : Observable<List<Post>> {
        Timber.d("getting posts for $section")
        return runRequest(client, sectionUrl(section))
                .flatMap({ parseHtml(it) })
    }

    // some other implementation of server could use different urls
    private fun sectionUrl(section: Section) : String {
        return when(section) {
            Section.Popular -> "http://advaitaworld.com"
            Section.Community -> "http://advaitaworld.com/blog/new"
            Section.Personal -> "http://advaitaworld.com/personal_blog/new"
            else -> throw RuntimeException("unknown section")
        }
    }
}

private fun parseHtml(inputStream: InputStream): Observable<List<Post>> {
    return Observable.create({ subscriber ->
        val reader = XMLReaderFactory.createXMLReader("org.ccil.cowan.tagsoup.Parser")
        val handler = AwHtmlHandler()
        reader.setContentHandler(handler)
        reader.parse(InputSource(inputStream))
        if(!subscriber.isUnsubscribed()) {
            subscriber.onNext(handler.getParsedData())
            subscriber.onCompleted()
        }
    })
}

// FIXME put in some common place, this is a generic method
private fun runRequest(client: OkHttpClient, url: String) : Observable<InputStream> {
    if(MOCK_PAGE_HTML != null) {
        Timber.d("USING MOCK DATA")
        return Observable.just(MOCK_PAGE_HTML)
    }
    return runOnce {
        Timber.d("starting request for url $url")
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if(!response.isSuccessful()) {
            throw IOException("unexpected http code: ${response.code()}")
        }
        Timber.d("got successful response for $url")
        response.body().byteStream()
    }
}

// FIXME remove
public var MOCK_PAGE_HTML: InputStream? = null
