package com.advaitaworld.app

import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes

public class AwHtmlHandler() : DefaultHandler() {
    //private enum class
    val posts : MutableList<Post> = arrayListOf()
    var isInsidePost : Boolean = false
    var inContentLevel: Int = -1
    var contentBuilder : StringBuilder? = null

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes?) {
        isInsidePost = isInsidePost || isPostElement(qName, attributes)
        if(!isInsidePost) {
            return
        }
        if(isPostContentStart(qName, attributes)) {
            inContentLevel = 0
            contentBuilder = StringBuilder()
        } else if(inContentLevel >= 0) {
            inContentLevel++
        }
        if(inContentLevel >= 1) {
            // all child tags of post are added as is (FIXME use spannables)
            contentBuilder!!.append('<')
            contentBuilder!!.append(qName)
            contentBuilder!!.append('>')
        }
        //Timber.d("tag $qName start, ${attributes(attributes)}., contentLevel now: $inContentLevel")
    }

    // helper used for debugging only
    private fun attributes(a: Attributes?) : Map<String, String> {
        if(a == null) { return mapOf() }
        val m : MutableMap<String, String> = hashMapOf()
        for(i in (0..a.getLength()-1)) {
            m.put(a.getQName(i), a.getValue(i))
        }
        return m
    }


    override fun endElement(uri: String, localName: String, qName: String) {
        if(!isInsidePost) {
            return
        }
        if(isPostElement(qName, null)) {
            // finished parsing this post element, sum up and save
            posts.add(Post(contentBuilder.toString().replaceAll("\\s+", " ").trim()))
            contentBuilder = null
            isInsidePost = false
            return
        }

        if(inContentLevel >= 1) {
            // all child tags of post are added as is (FIXME use spannables)
            contentBuilder!!.append('<')
            contentBuilder!!.append('/')
            contentBuilder!!.append(qName)
            contentBuilder!!.append('>')
        }
        if(inContentLevel >= 0) inContentLevel--
        //Timber.d("tag $qName end, contentLevel now: $inContentLevel")
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if(inContentLevel >= 0) {
            // for some reason kotlin String wrapper class lacks this constructor, have to use
            // a java.lang.String directly
            contentBuilder!!.append(ch, start, length)
        }
    }

    public fun getParsedData() : List<Post> {
        return posts
    }
}

private fun isPostElement(qName: String, attributes: Attributes?) : Boolean {
    return when(attributes) {
        null -> qName == "article"
        else ->  qName == "article" && attributes.getIndex("class") != -1 && attributes.getValue("class").contains("topic")
    }
}

private fun isPostContentStart(qName: String, attributes: Attributes?) : Boolean {
    val classAttr : String? = attributes?.getValue("class")
    return classAttr != null && classAttr.contains("topic-content")
}
