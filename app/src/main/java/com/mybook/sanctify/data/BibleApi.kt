package com.mybook.sanctify.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class CatalogItem(val id: String, val name: String, val language: String, val lang: String)

class ParsedBible(val id: String, val name: String, val language: String, val verses: List<VerseEntity>)

/**
 * Каталог и скачивание переводов с открытого API getBible (https://getbible.net).
 * Формат полного перевода: { translation, abbreviation, language, books: [ { nr, chapters: [ { chapter, verses: [ { verse, text } ] } ] } ] }.
 * Парсер терпим к тому, что массивы могут быть объектами с ключами-номерами.
 */
object BibleApi {
    private const val BASE = "https://api.getbible.net/v2"
    private val json = Json { ignoreUnknownKeys = true }

    fun fetch(url: String, onProgress: (Float) -> Unit = {}): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress((read.toFloat() / total).coerceAtMost(0.95f))
                }
            }
            return out.toString("UTF-8")
        } finally {
            c.disconnect()
        }
    }

    fun catalog(): List<CatalogItem> {
        val root = json.parseToJsonElement(fetch("$BASE/translations.json"))
        return root.items().mapNotNull { (key, el) ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["abbreviation"].str() ?: key
            CatalogItem(
                id = id,
                name = o["translation"].str() ?: id,
                language = o["language"].str() ?: "",
                lang = o["lang"].str() ?: "",
            )
        }
    }

    fun downloadUrl(id: String) = "$BASE/$id.json"

    /** [idOverride] — принудительный id перевода (иначе берётся abbreviation из файла). */
    fun parse(text: String, idOverride: String?): ParsedBible {
        val root = json.parseToJsonElement(text).jsonObject
        val id = idOverride ?: root["abbreviation"].str() ?: "custom"
        val name = root["translation"].str() ?: id
        val language = root["language"].str() ?: ""
        val verses = ArrayList<VerseEntity>(32_000)
        root["books"].items().forEachIndexed { bi, (bkey, bel) ->
            val bo = bel as? JsonObject ?: return@forEachIndexed
            val book = bo["nr"].int() ?: bkey.toIntOrNull() ?: (bi + 1)
            bo["chapters"].items().forEachIndexed { ci, (ckey, cel) ->
                val co = cel as? JsonObject ?: return@forEachIndexed
                val chapter = co["chapter"].int() ?: ckey.toIntOrNull() ?: (ci + 1)
                co["verses"].items().forEachIndexed { vi, (vkey, vel) ->
                    val vo = vel as? JsonObject ?: return@forEachIndexed
                    val verse = vo["verse"].int() ?: vkey.toIntOrNull() ?: (vi + 1)
                    val t = vo["text"].str()?.trim() ?: return@forEachIndexed
                    verses.add(VerseEntity(id, book, chapter, verse, t))
                }
            }
        }
        require(verses.isNotEmpty()) { "В файле не найдено ни одного стиха" }
        return ParsedBible(id, name, language, verses)
    }

    private fun JsonElement?.items(): List<Pair<String, JsonElement>> = when (this) {
        is JsonArray -> mapIndexed { i, e -> (i + 1).toString() to e }
        is JsonObject -> entries.map { it.key to it.value }
        else -> emptyList()
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
}
