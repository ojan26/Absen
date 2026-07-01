package com.example.utils

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class BlogPost(
    val title: String,
    val link: String,
    val pubDate: String,
    val description: String,
    val thumbnailUrl: String? = null
)

object BlogFeedHelper {
    fun fetchLatestPosts(): List<BlogPost> {
        val posts = mutableListOf<BlogPost>()
        var connection: HttpURLConnection? = null
        try {
            // Blogger RSS Feed for custom domains
            val url = URL("https://fasen.my.id/feeds/posts/default?alt=rss")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.connect()
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                posts.addAll(parseFeed(connection.inputStream))
            } else {
                // Try fallback WordPress format
                val fallbackUrl = URL("https://fasen.my.id/feed/")
                val fallbackConn = fallbackUrl.openConnection() as HttpURLConnection
                fallbackConn.requestMethod = "GET"
                fallbackConn.connectTimeout = 8000
                fallbackConn.readTimeout = 8000
                fallbackConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                fallbackConn.connect()
                if (fallbackConn.responseCode == HttpURLConnection.HTTP_OK) {
                    posts.addAll(parseFeed(fallbackConn.inputStream))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return posts.take(3)
    }

    private fun parseFeed(inputStream: InputStream): List<BlogPost> {
        val posts = mutableListOf<BlogPost>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            
            var eventType = parser.eventType
            var currentPost: BlogPostBuilder? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true) || tagName.equals("entry", ignoreCase = true)) {
                            currentPost = BlogPostBuilder()
                        } else if (currentPost != null) {
                            when {
                                tagName.equals("title", ignoreCase = true) -> {
                                    currentPost.title = parser.nextText()
                                }
                                tagName.equals("link", ignoreCase = true) -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (href != null) {
                                        currentPost.link = href
                                    } else {
                                        currentPost.link = parser.nextText()
                                    }
                                }
                                tagName.equals("pubDate", ignoreCase = true) || tagName.equals("published", ignoreCase = true) -> {
                                    currentPost.pubDate = parser.nextText()
                                }
                                tagName.equals("description", ignoreCase = true) || tagName.equals("content", ignoreCase = true) || tagName.equals("summary", ignoreCase = true) -> {
                                    currentPost.description = parser.nextText()
                                }
                                tagName.equals("media:thumbnail", ignoreCase = true) || tagName.equals("thumbnail", ignoreCase = true) -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (url != null) {
                                        currentPost.thumbnailUrl = url
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if ((tagName.equals("item", ignoreCase = true) || tagName.equals("entry", ignoreCase = true)) && currentPost != null) {
                            posts.add(currentPost.build())
                            currentPost = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return posts
    }
}

class BlogPostBuilder {
    var title: String = ""
    var link: String = ""
    var pubDate: String = ""
    var description: String = ""
    var thumbnailUrl: String? = null

    fun build(): BlogPost {
        if (thumbnailUrl == null && description.isNotEmpty()) {
            thumbnailUrl = extractImageUrl(description)
        }
        
        val cleanDesc = stripHtml(description)
        
        return BlogPost(
            title = title,
            link = link,
            pubDate = formatFriendlyDate(pubDate),
            description = cleanDesc.take(120) + if (cleanDesc.length > 120) "..." else "",
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun extractImageUrl(html: String): String? {
        val pattern = java.util.regex.Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun formatFriendlyDate(rawDate: String): String {
        return try {
            if (rawDate.contains(",")) {
                val inputSdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US)
                val date = inputSdf.parse(rawDate)
                java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id", "ID")).format(date)
            } else if (rawDate.contains("T")) {
                val cleanRaw = rawDate.split(".")[0].replace("Z", "")
                val inputSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                val date = inputSdf.parse(cleanRaw)
                java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id", "ID")).format(date)
            } else {
                rawDate
            }
        } catch (e: Exception) {
            rawDate
        }
    }
}
