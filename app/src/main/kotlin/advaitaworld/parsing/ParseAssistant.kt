package advaitaworld.parsing

import advaitaworld.net.Section
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.Reader
import java.util.regex.Pattern

public interface ParseAssistant {
    fun parsePostFeed(content: String, mediaResolver: MediaResolver): List<ShortPostInfo>
    fun parseUserProfile(name: String, html: String): User
    fun extractSecurityKey(content: Reader) : String
    fun extractLoggedUserName(content: Reader) : String
    fun urlProvider() : UrlProvider
}

interface UrlProvider {
    val baseUrl : String
    fun sectionUrl(section: Section) : String
    fun profileUrl(name: String) : String
    fun postUrl(postId: String) : String
    fun logoutUrl(securityKey: String) : String
    fun loginUrl() : String
}

public class LiveStreetUrlProvider(baseUrl : String) : UrlProvider {
    override val baseUrl = baseUrl

    override fun sectionUrl(section: Section) : String {
        return when(section) {
            Section.Popular -> baseUrl
            Section.Community -> "$baseUrl/blog"
            Section.Personal -> "$baseUrl/personal_blog"
            else -> throw RuntimeException("unknown section")
        }
    }

    // some other implementation of Server could use different urls
    override fun profileUrl(name: String) : String {
        return "$baseUrl/profile/$name"
    }

    override fun postUrl(postId: String) : String {
        return "$baseUrl/blog/$postId.html"
    }

    override fun logoutUrl(securityKey: String) : String {
        return "$baseUrl/login/exit/?security_ls_key=$securityKey"
    }

    override fun loginUrl() : String {
        return "$baseUrl/login/ajax-login/"
    }
}

public class AwParseAssistant : ParseAssistant {
    /**
     * Parses user profile page and returns an object with image url filled
     */
    override fun parseUserProfile(name: String, html: String): User {
        Timber.d("parsing profile for $name")
        val document = Jsoup.parse(html)
        val imgElem = document.select("div.profile-top > .avatar > img")
        val imgUrl = imgElem.get(0).attr("src")
        return User(name, imgUrl)
    }

    override fun parsePostFeed(content: String, mediaResolver: MediaResolver): List<ShortPostInfo> {
        val document = Jsoup.parse(content)
        val posts = document.select("article.topic")
        val parsedPosts = posts.map({ postElem ->
            val text = postElem.selectFirst(".topic-content").html()
            val author = postElem.selectFirst("a.user").text()
            val dateString = postElem.selectFirst(".topic-info-date > time").text()
            val commentElem = postElem.select(".topic-info-comments > a > span")
            val commentCount = if(!commentElem.isEmpty()) commentElem.get(0).text() else null
            val voteCountStr = postElem.selectFirst(".vote-count > span").text()
            val voteCount = parsePostVoteCount(voteCountStr)
            val postTitleElem = postElem.selectFirst("h2.topic-title > a")
            val postTitle = postTitleElem.text()
            val postLink = postTitleElem.attr("href")
            val postId = parsePostLink(postLink)!!
            val parsedPost = parseHtmlContent(text, mediaResolver)
            val shortenedPost = shortenForDisplay(parsedPost)
            val contentInfo = ContentInfo(author, shortenedPost, dateString, voteCount)
            ShortPostInfo(postId, postTitle, contentInfo,
                isExpandable = parsedPost.length() != shortenedPost.length(),
                commentCount = commentCount)
        })
        return parsedPosts
    }

    /**
     * Extracts a security key by parsing a html of main page
     */
    override fun extractSecurityKey(content: Reader) : String {
        Timber.e("searching for security key")
        val pattern = Pattern.compile("LIVESTREET_SECURITY_KEY.*'(.+)'.*")
        return matchLinewise(content, pattern) ?: throw RuntimeException("failed extract security ls key")
    }

    /**
     * Extracts a username of currently logged in user by parsing a html of main page.
     * Should be used on content fetched after a successful login
     */
    override fun extractLoggedUserName(content: Reader) : String {
        Timber.e("searching for user name")
        val pattern = Pattern.compile("footer-list-header.+img.+avatar.+>(.+):.*</li>")
        return matchLinewise(content, pattern) ?: throw RuntimeException("failed extract user logged name")
    }

    override fun urlProvider() : UrlProvider {
        return LiveStreetUrlProvider("http://advaitaworld.com")
    }
}

