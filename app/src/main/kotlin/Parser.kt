package com.advaitaworld.app

import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes

public class AwHtmlHandler() : DefaultHandler() {
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

    public fun getParsedData() : List<Post> {
        return posts
    }
}

