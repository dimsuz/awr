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
        val endAction: (List<Post>) -> Unit  = { list ->
            if(!subscriber.isUnsubscribed()) {
                subscriber.onNext(list)
                subscriber.onCompleted()
            }
        }
        reader.setContentHandler(HtmlHandler(endAction))
        Timber.d("about to start parsing of posts html")
        reader.parse(InputSource(inputStream))
        Timber.d("parsing is done")
    })
}

private class HtmlHandler(val endAction: (List<Post>) -> Unit) : DefaultHandler() {
    val posts : MutableList<Post> = arrayListOf()
    var contentBuilder : StringBuilder? = null

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        val isAtPost = contentBuilder != null
        if(!isAtPost && isPostElement(qName, attributes)) {
            contentBuilder = StringBuilder()
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        if(contentBuilder != null && isPostElement(qName, null)) {
            posts.add(Post(contentBuilder.toString().replaceAll("\\s+", " ").trim()))
            contentBuilder = null
        }
    }

    override fun endDocument() {
        endAction(posts)
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if(contentBuilder != null) {
            // for some reason kotlin String wrapper class lacks this constructor, have to use
            // a java.lang.String directly
            contentBuilder!!.append(ch, start, length)
        }
    }

    private fun isPostElement(qName: String, attributes: Attributes?) : Boolean {
        return when(attributes) {
            null -> qName == "article"
            else ->  qName == "article" && attributes.getIndex("class") != -1 && attributes.getValue("class").contains("topic")
        }
    }
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
