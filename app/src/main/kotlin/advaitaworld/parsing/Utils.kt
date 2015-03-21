package advaitaworld.parsing

import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Find first element which matches the cssQuery.
 * If no elements found, RuntimeException will be thrown
 */
private fun Element.selectFirst(cssQuery: String) : Element {
    val elements = this.select(cssQuery)
    if(elements.isEmpty()) {
        throw RuntimeException("failed to find node using css: $cssQuery")
    }
    return elements.get(0)
}

private val votePattern = Pattern.compile("^[+-]\\d+")
private fun parsePostVoteCount(s: String): String? {
    val m = votePattern.matcher(s)
    if(m.matches()) {
        return s
    } else {
        return null
    }
}

/**
 * Parses a post link and returns a post id.
 *
 * Example:
 * http://advaitaworld.com/blog/48581.html => "48581"
 */
private val postLinkPattern = Pattern.compile("^http://advaitaworld\\.com.+/(\\d+)\\.html")
private fun parsePostLink(link: String) : String? {
    val m = postLinkPattern.matcher(link)
    if(m.matches()) {
        return m.group(1)
    } else {
        return null
    }
}