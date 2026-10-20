package com.nanobuo.mypodder.data.opml

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

data class OpmlFeed(val title: String?, val url: String)

object Opml {
    fun parse(input: InputStream): List<OpmlFeed> {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val feeds = mutableListOf<OpmlFeed>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                val url = parser.getAttributeValue(null, "xmlUrl") ?: parser.getAttributeValue(null, "xmlurl")
                if (!url.isNullOrBlank()) {
                    val title = parser.getAttributeValue(null, "title") ?: parser.getAttributeValue(null, "text")
                    feeds += OpmlFeed(title, url.trim())
                }
            }
            event = parser.next()
        }
        return feeds.distinctBy { it.url }
    }

    fun write(output: OutputStream, feeds: List<OpmlFeed>) {
        val serializer = Xml.newSerializer()
        serializer.setOutput(output, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "opml").attribute(null, "version", "2.0")
        serializer.startTag(null, "head")
        serializer.startTag(null, "title").text("myPodder").endTag(null, "title")
        serializer.endTag(null, "head")
        serializer.startTag(null, "body")
        for (feed in feeds) {
            serializer.startTag(null, "outline")
            serializer.attribute(null, "type", "rss")
            serializer.attribute(null, "text", feed.title.orEmpty())
            serializer.attribute(null, "title", feed.title.orEmpty())
            serializer.attribute(null, "xmlUrl", feed.url)
            serializer.endTag(null, "outline")
        }
        serializer.endTag(null, "body")
        serializer.endTag(null, "opml")
        serializer.endDocument()
        output.flush()
    }
}
