package com.mybook.sanctify.data

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.zip.ZipFile

/** Работа с файлами библиотеки: определение формата, метаданные, превращение EPUB/FB2/TXT в абзацы. */
object Library {
    class Para(val text: String, val heading: Boolean)
    class Meta(val title: String?, val author: String?)

    fun dir(ctx: Context): File = File(ctx.filesDir, "library").apply { mkdirs() }

    /** По содержимому: pdf | epub | fb2 | fb2zip | txt. */
    fun detectFormat(file: File): String {
        val head = file.inputStream().use { s -> ByteArray(2048).let { b -> b.copyOf(maxOf(0, s.read(b))) } }
        val text = String(head, Charsets.ISO_8859_1)
        if (text.startsWith("%PDF")) return "pdf"
        if (head.size > 3 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            return try {
                ZipFile(file).use { z ->
                    when {
                        z.getEntry("META-INF/container.xml") != null -> "epub"
                        z.entries().asSequence().any { it.name.endsWith(".fb2", true) } -> "fb2zip"
                        else -> "txt"
                    }
                }
            } catch (e: Exception) {
                "txt"
            }
        }
        if (text.contains("FictionBook")) return "fb2"
        return "txt"
    }

    /** Достаёт .fb2 из архива .fb2.zip. */
    fun unzipFb2(zip: File, out: File) {
        ZipFile(zip).use { z ->
            val e = z.entries().asSequence().first { it.name.endsWith(".fb2", true) }
            z.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    // ---------- кэш абзацев ----------
    private fun cacheFile(file: File) = File(file.path + ".cache")

    fun load(file: File, format: String): List<Para> {
        val cache = cacheFile(file)
        if (cache.exists()) {
            val out = ArrayList<Para>()
            cache.bufferedReader().useLines { lines ->
                lines.forEach { l -> if (l.length > 2) out.add(Para(l.substring(2).replace("\\n", " "), l[0] == 'H')) }
            }
            if (out.isNotEmpty()) return out
        }
        val parsed = parse(file, format).second
        cache.bufferedWriter().use { w ->
            parsed.forEach { w.write((if (it.heading) "H\t" else "P\t") + it.text.replace('\n', ' ')); w.newLine() }
        }
        return parsed
    }

    fun parse(file: File, format: String): Pair<Meta, List<Para>> = when (format) {
        "epub" -> epub(file)
        "fb2" -> fb2(file)
        else -> txt(file)
    }

    // ---------- TXT ----------
    private fun decodeText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (!utf8.contains('\uFFFD')) utf8.removePrefix("\uFEFF") else String(bytes, Charset.forName("windows-1251"))
    }

    private fun txt(file: File): Pair<Meta, List<Para>> {
        val text = decodeText(file.readBytes()).replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        val blank = lines.count { it.isBlank() }
        val paras = ArrayList<Para>()
        if (blank * 20 < lines.size) {
            lines.forEach { l -> if (l.isNotBlank()) paras.add(Para(l.trim(), false)) }
        } else {
            val cur = StringBuilder()
            fun flush() {
                if (cur.isNotBlank()) paras.add(Para(cur.toString().trim(), false))
                cur.clear()
            }
            lines.forEach { l -> if (l.isBlank()) flush() else cur.append(l.trim()).append(' ') }
            flush()
        }
        return Meta(null, null) to paras
    }

    // ---------- HTML → абзацы ----------
    private val tagRe = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>")
    private val dropRe = Regex("<!--.*?-->|<(script|style)\\b.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val blockTags = setOf("p", "div", "br", "li", "tr", "blockquote", "section", "article", "ul", "ol", "table", "dd", "dt", "pre", "hr")
    private val entities = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "laquo" to "«", "raquo" to "»",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "bdquo" to "„", "copy" to "©", "shy" to "",
    )
    private val entityRe = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    private fun decode(s: String): String = entityRe.replace(s) { m ->
        val g = m.groupValues[1]
        when {
            g.startsWith("#x") -> g.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            g.startsWith("#") -> g.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> entities[g] ?: m.value
        }
    }

    private fun htmlToParas(html: String): List<Para> {
        val bodyStart = Regex("<body\\b[^>]*>", RegexOption.IGNORE_CASE).find(html)?.range?.last?.plus(1) ?: 0
        val body = dropRe.replace(html.substring(bodyStart), "")
        val out = ArrayList<Para>()
        val sb = StringBuilder()
        var heading = false
        fun flush() {
            val t = decode(sb.toString()).replace(Regex("\\s+"), " ").trim()
            if (t.isNotEmpty()) out.add(Para(t, heading))
            sb.clear()
        }
        var last = 0
        for (m in tagRe.findAll(body)) {
            sb.append(body, last, m.range.first)
            last = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            when {
                name.length == 2 && name[0] == 'h' && name[1].isDigit() -> { flush(); heading = !closing }
                name in blockTags -> flush()
                name == "td" || name == "th" -> sb.append(' ')
            }
        }
        sb.append(body, last, body.length)
        flush()
        return out
    }

    // ---------- EPUB ----------
    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").find(tag)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

    private fun resolve(base: String, href: String): String {
        val parts = ArrayList<String>()
        (if (base.isEmpty()) href else "$base/$href").split('/').forEach {
            when (it) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(it)
            }
        }
        return parts.joinToString("/")
    }

    private fun epub(file: File): Pair<Meta, List<Para>> = ZipFile(file).use { zip ->
        fun read(name: String): ByteArray? = zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        val container = read("META-INF/container.xml")?.toString(Charsets.UTF_8) ?: error("Это не EPUB")
        val opfPath = attr(Regex("<rootfile\\b[^>]*>").find(container)?.value ?: "", "full-path") ?: error("Нет описания книги")
        val opf = read(opfPath)?.toString(Charsets.UTF_8) ?: error("Нет описания книги")
        val base = opfPath.substringBeforeLast('/', "")
        val manifest = HashMap<String, String>()
        Regex("<item\\b[^>]*>").findAll(opf).forEach { m ->
            val id = attr(m.value, "id"); val href = attr(m.value, "href")
            if (id != null && href != null) manifest[id] = href
        }
        val title = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(opf)?.groupValues?.get(1)?.let { decode(it).trim() }
        val author = Regex("<dc:creator[^>]*>(.*?)</dc:creator>", RegexOption.DOT_MATCHES_ALL).find(opf)?.groupValues?.get(1)?.let { decode(it).trim() }
        val paras = ArrayList<Para>()
        Regex("<itemref\\b[^>]*>").findAll(opf).forEach { m ->
            val href = attr(m.value, "idref")?.let { manifest[it] } ?: return@forEach
            val path = resolve(base, URLDecoder.decode(href.substringBefore('#'), "UTF-8"))
            val bytes = read(path) ?: return@forEach
            paras.addAll(htmlToParas(decodeText(bytes)))
        }
        require(paras.isNotEmpty()) { "В книге нет текста" }
        Meta(title, author) to paras
    }

    // ---------- FB2 ----------
    private fun fb2(file: File): Pair<Meta, List<Para>> {
        val parser = android.util.Xml.newPullParser()
        file.inputStream().use { input ->
            parser.setInput(input, null)
            val stack = ArrayList<String>()
            val paras = ArrayList<Para>()
            val sb = StringBuilder()
            var bookTitle = ""
            val authorParts = ArrayList<String>()
            var inNotes = false
            var event = parser.eventType
            val textTags = setOf("p", "v", "subtitle", "text-author")
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        stack.add(parser.name)
                        if (parser.name == "body" && parser.getAttributeValue(null, "name") == "notes") inNotes = true
                    }
                    XmlPullParser.TEXT -> {
                        val t = parser.text
                        val inTitleInfo = "title-info" in stack
                        when {
                            inTitleInfo && stack.lastOrNull() == "book-title" -> bookTitle += t
                            inTitleInfo && "author" in stack && stack.lastOrNull() in setOf("first-name", "last-name") -> authorParts.add(t.trim())
                            "body" in stack && !inNotes && stack.any { it in textTags } -> sb.append(t)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val n = parser.name
                        if (n in textTags && "body" in stack && !inNotes) {
                            val t = sb.toString().replace(Regex("\\s+"), " ").trim()
                            if (t.isNotEmpty()) paras.add(Para(t, "title" in stack || n == "subtitle"))
                            sb.clear()
                        }
                        if (n == "body") inNotes = false
                        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                    }
                }
                event = parser.next()
            }
            require(paras.isNotEmpty()) { "В книге нет текста" }
            return Meta(bookTitle.trim().ifBlank { null }, authorParts.filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }) to paras
        }
    }
}
