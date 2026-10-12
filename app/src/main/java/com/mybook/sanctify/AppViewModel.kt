package com.mybook.sanctify

import android.app.Application
import android.content.Context
import android.net.Uri
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.mybook.sanctify.data.*
import com.mybook.sanctify.ui.FontOption
import com.mybook.sanctify.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class Pos(val book: Int, val chapter: Int)

enum class SearchMode(val title: String) { ALL("Все слова"), ANY("Любое слово"), PHRASE("Точная фраза") }

data class SearchOptions(
    val query: String,
    val mode: SearchMode,
    val matchCase: Boolean,
    val wholeWord: Boolean,
    val translationId: String,
    val fromBook: Int,
    val toBook: Int,
)

class SearchHit(val verse: VerseEntity, val ranges: List<IntRange>) {
    companion object {
        const val LIMIT = 500
    }
}

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val items: List<CatalogItem>, val warning: String? = null) : CatalogState
    data class Error(val message: String) : CatalogState
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = (app as App).db
    private val dao = db.dao()
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    // ---------- Настройки ----------
    var themeMode by mutableStateOf(
        ThemeMode.values().getOrElse(prefs.getInt("theme", 0)) { ThemeMode.PAPER }
    )
        private set
    var fontSize by mutableFloatStateOf(prefs.getFloat("font", 20f))
        private set

    fun setTheme(m: ThemeMode) {
        themeMode = m
        prefs.edit().putInt("theme", m.ordinal).apply()
    }

    var fontOption by mutableStateOf(
        FontOption.values().getOrElse(prefs.getInt("fontFamily", 0)) { FontOption.SERIF }
    )
        private set
    var lineSpacing by mutableFloatStateOf(prefs.getFloat("lineSpacing", 1.55f))
        private set
    var justify by mutableStateOf(prefs.getBoolean("justify", false))
        private set
    var boldText by mutableStateOf(prefs.getBoolean("bold", false))
        private set

    fun changeFontOption(o: FontOption) {
        fontOption = o
        prefs.edit().putInt("fontFamily", o.ordinal).apply()
    }

    fun changeLineSpacing(v: Float) {
        lineSpacing = v
        prefs.edit().putFloat("lineSpacing", v).apply()
    }

    fun changeJustify(v: Boolean) {
        justify = v
        prefs.edit().putBoolean("justify", v).apply()
    }

    fun changeBold(v: Boolean) {
        boldText = v
        prefs.edit().putBoolean("bold", v).apply()
    }

    fun changeFontSize(v: Float) {
        fontSize = v
        prefs.edit().putFloat("font", v).apply()
    }

    // ---------- Чтение ----------
    val translations = dao.translations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val trId = MutableStateFlow(prefs.getString("tr", "") ?: "")
    val pos = MutableStateFlow(Pos(prefs.getInt("book", 43), prefs.getInt("chapter", 1)))

    /** Стих, к которому нужно прокрутить после открытия главы. */
    var scrollTo by mutableStateOf<Int?>(null)

    val verses = combine(trId, pos) { t, p -> t to p }
        .flatMapLatest { (t, p) -> dao.chapter(t, p.book, p.chapter) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val highlights = pos.flatMapLatest { dao.highlights(it.book, it.chapter) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes = pos.flatMapLatest { dao.notes(it.book, it.chapter) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bookmarks = pos.flatMapLatest { dao.bookmarks(it.book, it.chapter) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val backStack = ArrayDeque<Pos>()
    private val fwdStack = ArrayDeque<Pos>()
    var canBack by mutableStateOf(false)
        private set
    var canForward by mutableStateOf(false)
        private set

    fun setTranslation(id: String) {
        trId.value = id
        prefs.edit().putString("tr", id).apply()
    }

    private fun savePos() {
        prefs.edit().putInt("book", pos.value.book).putInt("chapter", pos.value.chapter).apply()
        canBack = backStack.isNotEmpty()
        canForward = fwdStack.isNotEmpty()
    }

    fun goTo(book: Int, chapter: Int, verse: Int? = null) {
        val cur = pos.value
        if (cur.book != book || cur.chapter != chapter) {
            backStack.addLast(cur)
            fwdStack.clear()
        }
        pos.value = Pos(book, chapter)
        scrollTo = verse
        savePos()
    }

    fun goBack() {
        val p = backStack.removeLastOrNull() ?: return
        fwdStack.addLast(pos.value)
        pos.value = p
        scrollTo = null
        savePos()
    }

    fun goForward() {
        val p = fwdStack.removeLastOrNull() ?: return
        backStack.addLast(pos.value)
        pos.value = p
        scrollTo = null
        savePos()
    }

    fun step(delta: Int) {
        viewModelScope.launch {
            val p = pos.value
            if (delta > 0) {
                val count = dao.chapterCount(trId.value, p.book) ?: p.chapter
                if (p.chapter < count) goTo(p.book, p.chapter + 1)
                else if (p.book < Books.COUNT) goTo(p.book + 1, 1)
            } else {
                if (p.chapter > 1) goTo(p.book, p.chapter - 1)
                else if (p.book > 1) goTo(p.book - 1, dao.chapterCount(trId.value, p.book - 1) ?: 1)
            }
        }
    }

    suspend fun chapterCount(book: Int): Int = dao.chapterCount(trId.value, book) ?: 0

    suspend fun verseText(book: Int, chapter: Int, verse: Int): String? =
        dao.range(trId.value, book, chapter, verse, verse).firstOrNull()?.text

    /** Расширенный поиск: режимы «все слова / любое / фраза», регистр, целые слова, диапазон книг. */
    suspend fun search(o: SearchOptions): List<SearchHit> = withContext(Dispatchers.Default) {
        val q = o.query.trim()
        if (q.length < 2) return@withContext emptyList()
        val options = if (o.matchCase) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
        fun regexOf(body: String): Regex {
            val wrapped = if (o.wholeWord) "(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])" else body
            return Regex(wrapped, options)
        }
        val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val regexes = if (o.mode == SearchMode.PHRASE) {
            listOf(regexOf(words.joinToString("\\s+") { Regex.escape(it) }))
        } else {
            words.map { regexOf(Regex.escape(it)) }
        }
        val from = minOf(o.fromBook, o.toBook)
        val to = maxOf(o.fromBook, o.toBook)
        val hits = ArrayList<SearchHit>()
        for (v in dao.versesInBooks(o.translationId, from, to)) {
            val perRegex = regexes.map { r -> r.findAll(v.text).map { it.range }.toList() }
            val ok = if (o.mode == SearchMode.ANY) perRegex.any { it.isNotEmpty() } else perRegex.all { it.isNotEmpty() }
            if (ok) {
                hits.add(SearchHit(v, perRegex.flatten()))
                if (hits.size >= SearchHit.LIMIT) break
            }
        }
        hits
    }

    // ---------- Параллельный перевод ----------
    val parallelId = MutableStateFlow(prefs.getString("parallel", "") ?: "")
    val parallelVerses = combine(parallelId, pos) { t, p -> t to p }
        .flatMapLatest { (t, p) -> if (t.isBlank()) flowOf(emptyList()) else dao.chapter(t, p.book, p.chapter) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setParallel(id: String) {
        parallelId.value = id
        prefs.edit().putString("parallel", id).apply()
    }

    /** Текст одного стиха во всех установленных переводах. */
    suspend fun otherTranslations(book: Int, chapter: Int, verse: Int): List<Pair<TranslationEntity, String?>> =
        translations.value.map { t -> t to dao.range(t.id, book, chapter, verse, verse).firstOrNull()?.text }

    // ---------- Теги ----------
    val tags = dao.tags().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val verseTags = pos.flatMapLatest { dao.verseTags(it.book, it.chapter) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allVerseTags = dao.allVerseTags().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Если тег уже стоит на всех выбранных стихах — снимает его, иначе ставит на все. */
    fun toggleTag(tagId: Long, verses: Set<Int>) = viewModelScope.launch {
        val p = pos.value
        val have = verseTags.value.filter { it.tagId == tagId }.map { it.verse }.toSet()
        if (verses.all { it in have }) {
            dao.removeVerseTags(tagId, p.book, p.chapter, verses.toList())
        } else {
            dao.insertVerseTags(
                verses.filter { it !in have }.map { VerseTagEntity(tagId = tagId, book = p.book, chapter = p.chapter, verse = it) }
            )
        }
    }

    fun addTag(name: String) = viewModelScope.launch {
        val n = name.trim()
        if (n.isNotBlank() && tags.value.none { it.name.equals(n, ignoreCase = true) }) {
            dao.insertTag(TagEntity(name = n, color = TAG_COLORS[tags.value.size % TAG_COLORS.size]))
        }
    }

    fun deleteTag(id: Long) = viewModelScope.launch { dao.deleteTag(id) }

    fun removeVerseTag(tagId: Long, book: Int, chapter: Int, verse: Int) =
        viewModelScope.launch { dao.removeVerseTags(tagId, book, chapter, listOf(verse)) }

    // ---------- Пометки ----------
    fun highlight(verses: Set<Int>, color: Int, underline: Boolean) = viewModelScope.launch {
        val p = pos.value
        dao.clearWholeVerse(p.book, p.chapter, verses.toList(), underline)
        verses.forEach {
            dao.insertHighlight(
                HighlightEntity(
                    translationId = trId.value, book = p.book, chapter = p.chapter, verse = it,
                    startChar = -1, endChar = -1, color = color, underline = underline,
                )
            )
        }
    }

    fun highlightRange(verse: Int, start: Int, end: Int, color: Int, underline: Boolean) =
        viewModelScope.launch {
            val p = pos.value
            dao.insertHighlight(
                HighlightEntity(
                    translationId = trId.value, book = p.book, chapter = p.chapter, verse = verse,
                    startChar = start, endChar = end, color = color, underline = underline,
                )
            )
        }

    fun clearHighlights(verses: Set<Int>) = viewModelScope.launch {
        val p = pos.value
        dao.clearHighlights(p.book, p.chapter, verses.toList())
    }

    fun deleteHighlight(id: Long) = viewModelScope.launch { dao.deleteHighlight(id) }

    fun saveNote(existing: NoteEntity?, from: Int, to: Int, text: String) = viewModelScope.launch {
        val p = pos.value
        val now = System.currentTimeMillis()
        dao.upsertNote(
            existing?.copy(text = text, updatedAt = now)
                ?: NoteEntity(
                    book = p.book, chapter = p.chapter, verseStart = from, verseEnd = to,
                    text = text, createdAt = now, updatedAt = now,
                )
        )
    }

    fun deleteNote(id: Long) = viewModelScope.launch { dao.deleteNote(id) }

    fun toggleBookmark(verse: Int) = viewModelScope.launch {
        val p = pos.value
        val existing = dao.findBookmark(p.book, p.chapter, verse)
        if (existing != null) dao.deleteBookmark(existing.id)
        else dao.insertBookmark(
            BookmarkEntity(book = p.book, chapter = p.chapter, verse = verse, createdAt = System.currentTimeMillis())
        )
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { dao.deleteBookmark(id) }

    val allHighlights = dao.allHighlights().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allNotes = dao.allNotes().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allBookmarks = dao.allBookmarks().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ---------- Дневник ----------
    val sections = dao.sections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selectedSection = MutableStateFlow<Long?>(null)
    val entries = selectedSection
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.entries(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addSection(name: String, isList: Boolean = false) = viewModelScope.launch {
        val id = dao.insertSection(SectionEntity(name = name.trim(), sortOrder = sections.value.size, isList = isList))
        selectedSection.value = id
    }

    fun setSectionList(s: SectionEntity, isList: Boolean) =
        viewModelScope.launch { dao.updateSection(s.copy(isList = isList)) }

    // Пункты нумерованного списка — это записи раздела: заголовок = текст пункта.
    fun addListItem(sectionId: Long, text: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        dao.insertEntry(EntryEntity(sectionId = sectionId, title = text.trim(), body = "", createdAt = now, updatedAt = now))
    }

    fun editListItem(id: Long, text: String) =
        viewModelScope.launch { dao.updateTitle(id, text.trim(), System.currentTimeMillis()) }

    fun toggleDone(e: EntryEntity) = viewModelScope.launch { dao.setDone(e.id, !e.done) }

    fun renameSection(s: SectionEntity, name: String) =
        viewModelScope.launch { dao.updateSection(s.copy(name = name.trim())) }

    fun deleteSection(s: SectionEntity) = viewModelScope.launch {
        if (!s.isDefault) dao.deleteSection(s)
    }

    /** Создаёт запись; если переданы стихи текущей главы — прикрепляет их. */
    suspend fun createEntry(sectionId: Long, verses: Set<Int> = emptySet()): Long {
        val p = pos.value
        val now = System.currentTimeMillis()
        val title = if (verses.isEmpty()) "" else refText(p.book, p.chapter, verses.min(), verses.max())
        val id = dao.insertEntry(
            EntryEntity(sectionId = sectionId, title = title, body = "", createdAt = now, updatedAt = now)
        )
        for (r in verseRanges(verses)) addEntryVerse(id, p.book, p.chapter, r.first, r.last)
        selectedSection.value = sectionId
        return id
    }

    suspend fun loadEntry(id: Long) = dao.entry(id)

    fun saveEntry(id: Long, title: String, body: String) = viewModelScope.launch {
        dao.entry(id)?.let {
            if (it.title != title || it.body != body) {
                dao.updateEntry(it.copy(title = title, body = body, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteEntry(id: Long) = viewModelScope.launch { dao.deleteEntry(id) }

    fun entryVerses(entryId: Long) = dao.entryVerses(entryId)

    fun deleteEntryVerse(id: Long) = viewModelScope.launch { dao.deleteEntryVerse(id) }

    suspend fun addEntryVerse(entryId: Long, book: Int, chapter: Int, from: Int, to: Int): Boolean {
        val vs = dao.range(trId.value, book, chapter, from, to)
        if (vs.isEmpty()) {
            messages.emit("Такого стиха нет в выбранном переводе")
            return false
        }
        dao.insertEntryVerse(
            EntryVerseEntity(
                entryId = entryId, translationId = trId.value, book = book, chapter = chapter,
                verseStart = from, verseEnd = to, text = vs.joinToString(" ") { it.text },
            )
        )
        return true
    }

    // ---------- Переводы ----------
    val catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val downloads = MutableStateFlow<Map<String, Float>>(emptyMap())

    fun loadCatalog() = viewModelScope.launch {
        catalog.value = CatalogState.Loading
        var failure: String? = null
        val remote = try {
            withContext(Dispatchers.IO) { BibleApi.catalog() }
        } catch (e: Exception) {
            failure = e.message ?: e.javaClass.simpleName
            emptyList()
        }
        val items = BibleApi.extraCatalog + remote.filter { r -> BibleApi.extraCatalog.none { it.id == r.id } }
        catalog.value = CatalogState.Ready(
            items.sortedWith(compareBy({ !it.isRussian() }, { it.url == null }, { it.language }, { it.name })),
            failure,
        )
    }

    fun download(item: CatalogItem) = viewModelScope.launch {
        if (downloads.value.containsKey(item.id)) return@launch
        setProgress(item.id, 0f)
        try {
            val text = withContext(Dispatchers.IO) {
                BibleApi.fetch(item.url ?: BibleApi.downloadUrl(item.id)) { setProgress(item.id, it) }
            }
            val parsed = withContext(Dispatchers.Default) { BibleApi.parseAuto(text, item.id) }
            saveTranslation(TranslationEntity(item.id, item.name, item.language), parsed.verses)
            messages.emit("«${item.name}» установлен")
        } catch (e: Exception) {
            messages.emit("Не удалось скачать: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            downloads.update { it - item.id }
        }
    }

    /** Определяет формат по содержимому: модуль MySword (SQLite), JSON getBible или JSON-строки. */
    private fun parseBytes(bytes: ByteArray): ParsedBible =
        if (bytes.size > 15 && String(bytes, 0, 15, Charsets.ISO_8859_1) == "SQLite format 3") {
            val f = File(app.cacheDir, "import_module.db")
            f.writeBytes(bytes)
            try {
                BibleApi.parseMySword(f.path, null)
            } finally {
                f.delete()
            }
        } else {
            BibleApi.parseAuto(bytes.toString(Charsets.UTF_8), null)
        }

    /** Импорт из файла: JSON getBible, JSON-строки или модуль MySword (SQLite). */
    fun importFile(uri: Uri) = viewModelScope.launch {
        try {
            val parsed = withContext(Dispatchers.Default) {
                val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Не удалось открыть файл")
                parseBytes(bytes)
            }
            saveTranslation(TranslationEntity(parsed.id, parsed.name, parsed.language), parsed.verses)
            messages.emit("«${parsed.name}» импортирован")
        } catch (e: Exception) {
            messages.emit("Ошибка импорта: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Загрузка Библии по прямой ссылке на файл (тот же набор форматов). */
    fun importUrl(url: String) = viewModelScope.launch {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            messages.emit("Ссылка должна начинаться с http:// или https://")
            return@launch
        }
        messages.emit("Скачиваем…")
        try {
            val parsed = withContext(Dispatchers.IO) { parseBytes(BibleApi.fetchBytes(u)) }
            saveTranslation(TranslationEntity(parsed.id, parsed.name, parsed.language), parsed.verses)
            messages.emit("«${parsed.name}» установлен")
        } catch (e: Exception) {
            messages.emit("Не удалось загрузить: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun saveTranslation(t: TranslationEntity, verses: List<VerseEntity>) {
        dao.deleteVerses(t.id)
        verses.chunked(2000).forEach { dao.insertVerses(it) }
        dao.upsertTranslation(t) // последним: частично загруженный перевод не считается установленным
        if (trId.value.isBlank()) setTranslation(t.id)
    }

    fun deleteTranslation(id: String) = viewModelScope.launch {
        dao.deleteVerses(id)
        dao.deleteTranslation(id)
    }

    private fun setProgress(id: String, p: Float) = downloads.update { it + (id to p) }

    // ---------- Резервная копия ----------
    fun exportBackup(uri: Uri) = viewModelScope.launch {
        try {
            val b = Backup(
                sections = dao.listSections(), entries = dao.listEntries(),
                entryVerses = dao.listEntryVerses(), highlights = dao.listHighlights(),
                notes = dao.listNotes(), bookmarks = dao.listBookmarks(),
                tags = dao.listTags(), verseTags = dao.listVerseTags(),
            )
            val text = json.encodeToString(Backup.serializer(), b)
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    ?: error("Не удалось открыть файл")
            }
            messages.emit("Резервная копия сохранена")
        } catch (e: Exception) {
            messages.emit("Ошибка: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("Не удалось открыть файл")
            }
            val b = json.decodeFromString(Backup.serializer(), text)
            db.withTransaction {
                dao.clearEntryVerses(); dao.clearEntries(); dao.clearSections()
                dao.clearAllHighlights(); dao.clearNotes(); dao.clearBookmarks()
                if (b.tags.isNotEmpty()) { dao.clearVerseTags(); dao.clearTags() }
                dao.putSections(b.sections); dao.putEntries(b.entries); dao.putEntryVerses(b.entryVerses)
                dao.putHighlights(b.highlights); dao.putNotes(b.notes); dao.putBookmarks(b.bookmarks)
                if (b.tags.isNotEmpty()) { dao.putTags(b.tags); dao.putVerseTags(b.verseTags) }
            }
            messages.emit("Данные восстановлены")
        } catch (e: Exception) {
            messages.emit("Не удалось восстановить: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    init {
        viewModelScope.launch {
            translations.collect { list ->
                if (list.isNotEmpty() && list.none { it.id == trId.value }) setTranslation(list.first().id)
            }
        }
        viewModelScope.launch {
            dao.sections().collect { list ->
                if (list.isNotEmpty() && list.none { it.id == selectedSection.value }) {
                    selectedSection.value = list.first().id
                }
            }
        }
    }
}

fun CatalogItem.isRussian() = lang.startsWith("ru", true) || language.contains("russ", true)
fun CatalogItem.isEnglish() = lang.startsWith("en", true) || language.contains("english", true)
