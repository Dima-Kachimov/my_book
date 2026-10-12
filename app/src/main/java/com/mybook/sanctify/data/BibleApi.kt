package com.mybook.sanctify.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class CatalogItem(
    val id: String,
    val name: String,
    val language: String,
    val lang: String,
    /** Прямая ссылка на файл; если null — берётся из каталога getBible. */
    val url: String? = null,
    val note: String = "",
)

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

    private const val BIBLEAPI = "https://raw.githubusercontent.com/bibleapi/bibleapi-bibles-json/master"

    /**
     * Дополнительные русские переводы из стороннего открытого репозитория (формат: один стих — одна JSON-строка).
     * Тексты защищены авторским правом правообладателей — приложение лишь скачивает их для личного чтения.
     */
    val extraCatalog = listOf(
        CatalogItem(
            "crtb", "Современный перевод (CRTB)", "Russian", "ru", "$BIBLEAPI/crtb.json",
            "Библия, современный русский перевод. В источнике нет 4 Царств. Авторские права у правообладателя.",
        ),
        CatalogItem(
            "nrt", "Новый русский перевод (NRT)", "Russian", "ru", "$BIBLEAPI/nrt.json",
            "Новый русский перевод. В источнике нет 4 Царств. Авторские права у правообладателя.",
        ),
    )

    private val BOOK_IDS = listOf(
        "Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam", "1Kgs", "2Kgs",
        "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov", "Eccl", "Song", "Isa", "Jer", "Lam",
        "Ezek", "Dan", "Hos", "Joel", "Amos", "Obad", "Jona", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech",
        "Mal", "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph", "Phil", "Col",
        "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas", "1Pet", "2Pet", "1John",
        "2John", "3John", "Jude", "Rev",
    )
    private val bookNumbers: Map<String, Int> =
        BOOK_IDS.mapIndexed { i, id -> id to i + 1 }.toMap() + mapOf("Jonah" to 32, "Nahum" to 34, "Phlp" to 50)

    /** Определяет формат по содержимому: getBible-JSON или JSON-строки. */
    fun parseAuto(text: String, idOverride: String?): ParsedBible =
        if (text.trimStart().take(500).contains("\"book_id\"")) parseJsonLines(text, idOverride)
        else parse(text, idOverride)

    private fun parseJsonLines(text: String, idOverride: String?): ParsedBible {
        var id = idOverride
        val verses = ArrayList<VerseEntity>(32_000)
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val o = json.parseToJsonElement(line) as? JsonObject ?: return@forEach
            if (id == null) id = o["translation_id"].str()?.lowercase() ?: "custom"
            val book = bookNumbers[o["book_id"].str() ?: ""] ?: return@forEach
            val chapter = o["chapter"].int() ?: return@forEach
            val verse = o["verse"].int() ?: return@forEach
            val t = o["text"].str()?.trim() ?: return@forEach
            verses.add(VerseEntity(id!!, book, chapter, verse, t))
        }
        require(verses.isNotEmpty()) { "В файле не найдено ни одного стиха" }
        val finalId = id ?: "custom"
        return ParsedBible(finalId, finalId.uppercase(), "", verses)
    }

    private val rfRe = Regex("<RF>.*?<Rf>", RegexOption.DOT_MATCHES_ALL)
    private val tsRe = Regex("<TS>.*?<Ts>", RegexOption.DOT_MATCHES_ALL)
    private val tagRe = Regex("<[^>]*>")
    private val spaceRe = Regex("\\s+")

    private fun cleanMySword(s: String): String =
        s.replace(rfRe, "").replace(tsRe, "").replace(tagRe, "").replace("&nbsp;", " ")
            .replace(spaceRe, " ").trim()

    /** Модуль MySword (.bbl.mybible / SQLite): таблицы Bible(Book, Chapter, Verse, Scripture) и Details. */
    fun parseMySword(path: String, idOverride: String?): ParsedBible {
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            var abbr: String? = null
            var title: String? = null
            try {
                db.rawQuery("SELECT Description, Abbreviation FROM Details LIMIT 1", null).use { c ->
                    if (c.moveToFirst()) {
                        title = c.getString(0)
                        abbr = c.getString(1)
                    }
                }
            } catch (e: Exception) {
                // метаданные необязательны
            }
            val cleanAbbr = abbr?.lowercase()?.replace(Regex("[^a-z0-9а-я_]"), "") ?: ""
            val id = idOverride ?: cleanAbbr.ifBlank { "mysword" }
            val verses = ArrayList<VerseEntity>(32_000)
            db.rawQuery("SELECT Book, Chapter, Verse, Scripture FROM Bible", null).use { c ->
                while (c.moveToNext()) {
                    val t = cleanMySword(c.getString(3) ?: "")
                    if (t.isNotBlank()) verses.add(VerseEntity(id, c.getInt(0), c.getInt(1), c.getInt(2), t))
                }
            }
            require(verses.isNotEmpty()) { "В модуле не найдено ни одного стиха" }
            val name = title?.takeIf { it.isNotBlank() } ?: id
            return ParsedBible(id, name, "", verses)
        } finally {
            db.close()
        }
    }

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
