package advaitaworld.parsing

import org.jsoup.nodes.Element
import java.io.BufferedReader
import java.io.Reader
import java.util.regex.Pattern

//
// Contains some common parsing utility functions
//

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
private val postLinkPattern = Pattern.compile("^http://.+/(\\d+)\\.html")
private fun parsePostLink(link: String) : String? {
    val m = postLinkPattern.matcher(link)
    if(m.matches()) {
        return m.group(1)
    } else {
        return null
    }
}

/**
 * Searches content line-by-line until pattern matches, returns result of group(1) on match
 */
public fun matchLinewise(content: Reader, pattern: Pattern) : String? {
    val reader = BufferedReader(content)
    try {
        // FIXME these two lines are here instead of just reader.lines().filter(...)
        // because of some weird type inference bug of kotlin compiler
        // see https://devnet.jetbrains.com/message/5540845, and remove them after it's fixed
        // upd: even worse, it crashes at runtime with LinkageError, rewriting in imperative style...
        var line = reader.readLine()
        while(line != null) {
            val matcher = pattern.matcher(line)
            if(matcher.find()) {
                return matcher.group(1)
            }
            line = reader.readLine()
        }
        return null
    } finally {
        reader.close()
    }
}

