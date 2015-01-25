package com.advaitaworld.app.parse

import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import android.text.SpannableStringBuilder
import com.advaitaworld.app.Post
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.text.Spanned
import java.util.ArrayDeque
import java.util.regex.Pattern
import android.graphics.Color
import android.text.style.BackgroundColorSpan

public class AwHtmlHandler() : DefaultHandler() {
    val posts : MutableList<Post> = arrayListOf()
    var isInsidePost : Boolean = false
    /**
     * Actual post content can have child tags.
     * This variable specifies how far down in content of the post parser is.
     * <article> <- post start, level = -1
     *  <div class="topic-content">
     *    <!-- level is 0 here -->
     *    <p>
     *     text <- level 1
     *    </p>
     *  </div>
     * </article>
     */
    var inContentLevel = -1
    var contentBuilder : SpannableStringBuilder? = null
    var childContentTags : ArrayDeque<Int> = ArrayDeque()
    var authorBuilder : StringBuilder? = null
    var author : CharSequence? = null

    public fun getParsedData() : List<Post> {
        return posts
    }

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes?) {
        isInsidePost = isInsidePost || isPostElement(qName, attributes)
        if(!isInsidePost) {
            return
        }
        handleContentStart(qName, attributes)
        handleAuthorStart(qName, attributes)
        //Timber.d("tag $qName start, ${attributes(attributes)}., contentLevel now: $inContentLevel")
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        if(!isInsidePost) {
            return
        }
        if(isPostElement(qName, null)) {
            // finished parsing this post element, sum up and save
            posts.add(Post(author = author!!, content = contentBuilder!!))
            contentBuilder = null
            isInsidePost = false
            return
        }

        handleContentEnd(qName)
        handleAuthorEnd(qName)
        //Timber.d("tag $qName end, contentLevel now: $inContentLevel")
    }

    val replacePattern = Pattern.compile("(\\s{2,}|\n)")


    override fun characters(ch: CharArray, start: Int, length: Int) {
        handleContentChars(ch, start, length)
        handleAuthorChars(ch, start, length)
    }

    private fun handleContentStart(qName: String, attributes: Attributes?) {
        if(isPostContentStart(qName, attributes)) {
            inContentLevel = 0
            contentBuilder = SpannableStringBuilder()
            childContentTags.clear()
        } else if(inContentLevel >= 0) {
            inContentLevel++
        }
        if(inContentLevel >= 1) {
            childContentTags.addLast(contentBuilder!!.length())
        }
    }

    private fun handleContentEnd(qName: String) {
        if(inContentLevel >= 1) {
            // here the fact that most inner elements will end first is used
            // so that things like <b1><i><b2></b2></i></b1> should work - b2 will be added last
            // and taken out of the queue first
            val start = childContentTags.removeLast()
            processPostContentTag(qName, start, contentBuilder!!.length(), contentBuilder!!)
        }
        if(inContentLevel >= 0) inContentLevel--
    }

    private fun handleContentChars(ch: CharArray, start: Int, length: Int) {
        if(inContentLevel >= 0) {
            var s = java.lang.String(ch, start, length) as String
            val matcher = replacePattern.matcher(s)
            s = matcher.replaceAll("")
            // remember that this function might be called multiple times with only parts of
            // the content. So can't trim on both sides - not possible to know if this is the last part
            // of whole content, but possible to know if it's first
            if(contentBuilder!!.length() == 0) {
                s.trimLeading()
            }
            contentBuilder!!.append(s)
        }
    }

    private fun handleAuthorStart(qName: String, attributes: Attributes?) {
        if(isAuthorInfoStart(qName, attributes)) {
            authorBuilder = StringBuilder()
        }
    }

    private fun handleAuthorEnd(qName: String) {
        if(authorBuilder != null && isAuthorInfoEnd(qName)) {
            author = authorBuilder
            authorBuilder = null
        }
    }

    private fun handleAuthorChars(ch: CharArray, start: Int, length: Int) {
        authorBuilder?.append(ch, start, length)
    }
}

/**
 * Gets called when some inner post tag ended and is ready to be styled/processed
 */
private fun processPostContentTag(qName: String, start: Int, end: Int, content: SpannableStringBuilder) {
    //Timber.d("processing child tag '$qName', start=$start, end=$end")
    val span : Any? = when(qName) {
        "strong", "b" -> StyleSpan(Typeface.BOLD)
        "em", "i" -> StyleSpan(Typeface.ITALIC)
         // FIXME implement own LineBackgroundSpan, make it extend to the whole lines
        "blockquote" -> BackgroundColorSpan(Color.LTGRAY)
        else -> null
    }
    val isBlockElement = qName == "blockquote"

    // block elements need explicit newlines around them
    var s = start; var e = end
    if(isBlockElement) {
        content.insert(start, "\n")
        content.append('\n')
        s += 1; e += 1
    }

    if(span != null) {
        content.setSpan(span, s, e, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }
    if(qName == "br") {
        content.append('\n')
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

private fun isAuthorInfoStart(qName: String, attributes: Attributes?) : Boolean {
    return qName == "a" && attributes?.getIndex("class") != -1 && attributes!!.getValue("class").contains("user")
}

private fun isAuthorInfoEnd(qName: String) : Boolean {
    return qName == "a"
}

// helper used for debugging only
//private fun attributes(a: Attributes?) : Map<String, String> {
//    if(a == null) { return mapOf() }
//    val m : MutableMap<String, String> = hashMapOf()
//    for(i in (0..a.getLength()-1)) {
//        m.put(a.getQName(i), a.getValue(i))
//    }
//    return m
//}

