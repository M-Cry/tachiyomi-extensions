package eu.kanade.tachiyomi.extension.ar.team1x1

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Team1x1 : ParsedHttpSource() {
    override val name = "team1x1"

    override val baseUrl = "https://teamx.fun"

    override val lang = "ar"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.bsx"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a[title]").let {
                title = element.select("div.tt.float-right").text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "a.page-link[rel=next]"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "div.uta"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val lazysrc = element.select("img").attr("data-pagespeed-lazy-src")
        thumbnail_url = if (lazysrc.isNullOrEmpty()) {
            element.select("img").attr("abs:src")
        } else {
            lazysrc
        }
        setUrlWithoutDomain(element.select("a:has(img)").attr("abs:href"))
        title = element.select("a>h3").text()
    }

    override fun latestUpdatesNextPageSelector() = "a.page-link[rel=next]"

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/ajax/search?keyword=$query", headers)

    override fun searchMangaSelector() =
        "ol.list-group> li.list-group-item.d-flex.justify-content-between.align-items-start"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val lazysrc = element.select("img").attr("data-pagespeed-lazy-src")
        thumbnail_url = if (lazysrc.isNullOrEmpty()) {
            element.select("img").attr("abs:src")
        } else {
            lazysrc
        }
        setUrlWithoutDomain(element.select("div.image-parent> a").attr("href"))
        title = element.select("a.fw-bold").text()
    }
    override fun searchMangaNextPageSelector(): String? = null

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {

        return SManga.create().apply {
            thumbnail_url = document.select("img.shadow-sm").attr("abs:src")
            title = document.select("div.author-info-title.mb-3 > h1").text()
            author = artist
            artist = document.select("div:nth-child(7) small:nth-child(2) a").text()
            status = parseStatus(document.select("div:nth-child(6) > small:nth-child(2) > a").text())
            description = document.select(".review-content > p").text()
            genre = document.select("div.review-author-info > a").joinToString { it.text() }
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("مستمرة", true) -> SManga.ONGOING
        status.contains("متوقف", true) -> SManga.ON_HIATUS
        status.contains("مكتمل", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "div.ts-chl-collapsible-content ul >li"

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // Chapter list may be paginated, get recursively
        fun addChapters(document: Document) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            document.select("${popularMangaNextPageSelector()}:not([id])").firstOrNull()
                ?.let { addChapters(client.newCall(GET(it.attr("abs:href"), headers)).execute().asJsoup()) }
        }

        addChapters(response.asJsoup())
        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                setUrlWithoutDomain(it.attr("href"))
                name = element.select("div.epl-num+div.epl-num").text()
            }
            date_upload = parseChapterDate(element.select("div.epl-date").text())
        }
    }
    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.US).parse(date)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-break.no-gaps img[src]").mapIndexed { i, img ->
            Page(i, "", img.attr(if (img.hasAttr("data-src")) "abs:data-src" else "abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
