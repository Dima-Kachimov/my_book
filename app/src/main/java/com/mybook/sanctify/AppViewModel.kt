package com.mybook.sanctify

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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

    fun toggleDone(e: EntryEntity) = viewModelScope.launch {
        dao.setDone(e.id, !e.done)
        if (!e.done) Reminders.cancelDue(app, e.id) else Reminders.scheduleDue(app, e.id, e.title, e.dueAt)
    }

    /** Срок для пункта списка (millis или 0 — убрать). */
    fun setDue(e: EntryEntity, millis: Long) = viewModelScope.launch {
        dao.setDue(e.id, millis)
        Reminders.cancelDue(app, e.id)
        if (millis > 0 && !e.done) Reminders.scheduleDue(app, e.id, e.title, millis)
    }

    fun renameSection(s: SectionEntity, name: String) =
        viewModelScope.launch { dao.updateSection(s.copy(name = name.trim())) }

    fun deleteSection(s: SectionEntity) = viewModelScope.launch {
        if (!s.isDefault) {
            withContext(Dispatchers.IO) { dao.attachmentsInSection(s.id).forEach { deleteAttachmentFile(it) } }
            dao.deleteSection(s)
        }
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

    fun saveEntry(id: Long, title: String, body: String, rich: String) = viewModelScope.launch {
        dao.entry(id)?.let {
            if (it.title != title || it.body != body || it.bodyRich != rich) {
                dao.updateEntry(it.copy(title = title, body = body, bodyRich = rich, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteEntry(id: Long) = viewModelScope.launch {
        Reminders.cancelDue(app, id)
        withContext(Dispatchers.IO) { dao.attachmentsOf(id).forEach { deleteAttachmentFile(it) } }
        dao.deleteEntry(id)
    }

    fun entryVerses(entryId: Long) = dao.entryVerses(entryId)

    fun deleteEntryVerse(id: Long) = viewModelScope.launch { dao.deleteEntryVerse(id) }

    // ---------- Выбор места из Библии для вставки в запись ----------
    /** Режим выбора: Библия открыта из записи/документа. entryId > 0 — запись (можно прикрепить стих). */
    data class BiblePick(val entryId: Long, val caret: Int)
    class PendingQuote(val text: String, val ref: String, val caret: Int)

    var biblePick by mutableStateOf<BiblePick?>(null)
        private set
    var pendingQuote by mutableStateOf<PendingQuote?>(null)
        private set

    fun startBiblePick(entryId: Long, caret: Int) { biblePick = BiblePick(entryId, caret) }
    fun cancelBiblePick() { biblePick = null }
    fun finishBiblePick(text: String, ref: String) {
        val pick = biblePick ?: return
        pendingQuote = PendingQuote(text, ref, pick.caret)
        biblePick = null
    }
    fun takePendingQuote(): PendingQuote? = pendingQuote.also { pendingQuote = null }

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
    var backupIncludeBooks by mutableStateOf(prefs.getBoolean("bk_books", true))
        private set
    var backupIncludeBibles by mutableStateOf(prefs.getBoolean("bk_bibles", true))
        private set

    fun setBackupOptions(books: Boolean, bibles: Boolean) {
        backupIncludeBooks = books
        backupIncludeBibles = bibles
        prefs.edit().putBoolean("bk_books", books).putBoolean("bk_bibles", bibles).apply()
    }

    /** Полная копия в выбранный файл (ZIP): данные, настройки, книги, тексты Библий — по настройкам. */
    fun exportBackup(uri: Uri) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val out = app.contentResolver.openOutputStream(uri) ?: error("Не удалось открыть файл")
                BackupManager.writeZip(app, out, backupIncludeBooks, backupIncludeBibles, backupIncludeMedia)
            }
            messages.emit("Резервная копия сохранена")
        } catch (e: Exception) {
            messages.emit("Ошибка: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val input = app.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
                BackupManager.restore(app, input)
            }
            reloadSettings()
            Reminders.rescheduleAll(app)
            messages.emit("Всё восстановлено")
        } catch (e: Exception) {
            messages.emit("Не удалось восстановить: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Перечитывает настройки из SharedPreferences (после восстановления из копии). */
    private fun reloadSettings() {
        themeMode = ThemeMode.values().getOrElse(prefs.getInt("theme", 0)) { ThemeMode.PAPER }
        fontSize = prefs.getFloat("font", 20f)
        fontOption = FontOption.values().getOrElse(prefs.getInt("fontFamily", 0)) { FontOption.SERIF }
        lineSpacing = prefs.getFloat("lineSpacing", 1.55f)
        justify = prefs.getBoolean("justify", false)
        boldText = prefs.getBoolean("bold", false)
        trId.value = prefs.getString("tr", "") ?: ""
        pos.value = Pos(prefs.getInt("book", 43), prefs.getInt("chapter", 1))
        parallelId.value = prefs.getString("parallel", "") ?: ""
        readingReminder = Reminders.config(app, Reminders.READING)
        prayerReminder = Reminders.config(app, Reminders.PRAYER)
        backupIncludeBooks = prefs.getBoolean("bk_books", true)
        backupIncludeBibles = prefs.getBoolean("bk_bibles", true)
        backupIncludeMedia = prefs.getBoolean("bk_media", true)
        pinEnabled = prefs.getString("pin_hash", null) != null
        lockDelaySec = prefs.getInt("lock_delay", 60)
        secureScreen = prefs.getBoolean("secure_screen", false)
    }

    // ---------- Автокопия ----------
    var backupFolder by mutableStateOf(prefs.getString("backup_tree", null)?.let { folderLabel(Uri.parse(it)) })
        private set
    var autoBackupOn by mutableStateOf(prefs.getBoolean("backup_on", false))
        private set
    var lastBackup by mutableStateOf(prefs.getLong("backup_last", 0L))
        private set

    private fun folderLabel(uri: Uri) = Uri.decode(uri.lastPathSegment ?: uri.toString())

    fun setBackupFolder(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // некоторые провайдеры не поддерживают постоянный доступ
        }
        prefs.edit().putString("backup_tree", uri.toString()).apply()
        backupFolder = folderLabel(uri)
    }

    fun setAutoBackup(on: Boolean) {
        autoBackupOn = on
        prefs.edit().putBoolean("backup_on", on).apply()
        BackupManager.schedule(app, on && backupFolder != null)
    }

    fun backupNow() = viewModelScope.launch {
        try {
            val ok = withContext(Dispatchers.IO) { BackupManager.runNow(app) }
            if (ok) {
                lastBackup = System.currentTimeMillis()
                messages.emit("Копия сохранена в папку")
            } else {
                messages.emit("Сначала выберите папку для копий")
            }
        } catch (e: Exception) {
            messages.emit("Не удалось сохранить копию: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---------- Напоминания ----------
    var readingReminder by mutableStateOf(Reminders.config(app, Reminders.READING))
        private set
    var prayerReminder by mutableStateOf(Reminders.config(app, Reminders.PRAYER))
        private set

    fun setReminder(kind: String, c: ReminderConfig) {
        Reminders.saveConfig(app, kind, c)
        if (kind == Reminders.READING) readingReminder = c else prayerReminder = c
    }

    // ---------- «Сегодня» ----------
    val openPrayers = dao.openPrayers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val latestEntry = dao.latestEntry().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------- Заучивание ----------
    val memos = dao.memos().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addMemos(verses: Set<Int>) = viewModelScope.launch {
        val p = pos.value
        val now = System.currentTimeMillis()
        dao.insertMemos(
            verseRanges(verses).map {
                MemoEntity(book = p.book, chapter = p.chapter, verseStart = it.first, verseEnd = it.last, dueAt = now, addedAt = now)
            }
        )
        messages.emit("Добавлено в заучивание")
    }

    fun deleteMemo(id: Long) = viewModelScope.launch { dao.deleteMemo(id) }

    /** rating: 0 — не помню, 1 — трудно, 2 — помню. Интервалы по ступеням: 1, 3, 7, 14, 30, 90, 180 дней. */
    fun review(m: MemoEntity, rating: Int) = viewModelScope.launch {
        val day = 24L * 60 * 60 * 1000
        val intervals = longArrayOf(1, 3, 7, 14, 30, 90, 180)
        val now = System.currentTimeMillis()
        val updated = when (rating) {
            0 -> m.copy(box = 0, dueAt = now + 10 * 60 * 1000)
            1 -> m.copy(dueAt = now + day)
            else -> {
                val box = minOf(m.box + 1, intervals.size - 1)
                m.copy(box = box, dueAt = now + intervals[m.box.coerceIn(0, intervals.size - 1)] * day)
            }
        }
        dao.updateMemo(updated)
    }

    /** Текст диапазона стихов в текущем переводе. */
    suspend fun rangeText(book: Int, chapter: Int, from: Int, to: Int): String? =
        dao.range(trId.value, book, chapter, from, to).takeIf { it.isNotEmpty() }?.joinToString(" ") { it.text }

    // ---------- Перекрёстные ссылки и словарь ----------
    suspend fun crossRefs(book: Int, chapter: Int, verse: Int): List<Pair<AssetData.XRef, String?>> =
        withContext(Dispatchers.IO) {
            AssetData.crossRefs(app, book, chapter, verse).map { it to rangeText(it.book, it.chapter, it.from, it.to) }
        }

    suspend fun originalWords(book: Int, chapter: Int, verse: Int): List<AssetData.Word> =
        withContext(Dispatchers.IO) { AssetData.words(app, book, chapter, verse) }

    suspend fun lexicon(strong: String): AssetData.LexEntry? =
        withContext(Dispatchers.IO) { AssetData.lexicon(app, strong) }

    // ---------- Поиск по дневнику ----------
    class DiaryHit(val entry: EntryEntity, val section: String, val snippet: String, val isList: Boolean)

    suspend fun searchDiary(q: String): List<DiaryHit> = withContext(Dispatchers.Default) {
        val query = q.trim().lowercase()
        if (query.length < 2) return@withContext emptyList()
        val sections = dao.listSections().associateBy { it.id }
        val verseTexts = dao.listEntryVerses().groupBy { it.entryId }
        val docs = dao.listAttachments().filter { it.kind == "doc" || it.kind == "file" }.groupBy { it.entryId }
        dao.listEntries().sortedByDescending { it.updatedAt }.mapNotNull { e ->
            val inVerses = verseTexts[e.id].orEmpty().firstOrNull { it.text.lowercase().contains(query) }
            val inDocs = docs[e.id].orEmpty().firstOrNull {
                it.title.lowercase().contains(query) || it.docText.lowercase().contains(query)
            }
            val hay = when {
                e.title.lowercase().contains(query) -> e.title
                e.body.lowercase().contains(query) -> e.body
                inVerses != null -> inVerses.text
                inDocs != null && inDocs.docText.lowercase().contains(query) -> inDocs.docText
                inDocs != null -> inDocs.title
                else -> null
            } ?: return@mapNotNull null
            val i = hay.lowercase().indexOf(query).coerceAtLeast(0)
            val snippet = hay.substring((i - 30).coerceAtLeast(0), (i + 90).coerceAtMost(hay.length)).replace('\n', ' ')
            val sec = sections[e.sectionId]
            DiaryHit(e, sec?.name ?: "", snippet, sec?.isList == true)
        }
    }

    // ---------- Вложения записей ----------
    fun attachments(entryId: Long) = dao.attachments(entryId)

    suspend fun attachment(id: Long): AttachmentEntity? = dao.attachment(id)

    private fun deleteAttachmentFile(a: AttachmentEntity) {
        if (a.fileName.isNotBlank()) File(Media.dir(app), a.fileName).delete()
    }

    /** Добавляет файлы к записи. [forceKind] — «photo»/«video»; иначе тип определяется по содержимому. */
    fun addAttachments(entryId: Long, uris: List<Uri>, forceKind: String? = null) = viewModelScope.launch {
        var ok = 0
        for (u in uris) {
            try {
                withContext(Dispatchers.IO) {
                    val c = Media.copy(app, u)
                    dao.insertAttachment(
                        AttachmentEntity(
                            entryId = entryId, kind = forceKind ?: Media.kindFor(c.mime, c.name), title = c.name,
                            fileName = c.file.name, mime = c.mime, sizeBytes = c.file.length(), createdAt = System.currentTimeMillis(),
                        )
                    )
                }
                ok++
            } catch (e: Exception) {
                messages.emit("Не удалось добавить файл: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        if (ok > 0) messages.emit(if (ok == 1) "Добавлено" else "Добавлено: $ok")
    }

    /** Новый пустой документ внутри записи. */
    suspend fun createDocument(entryId: Long): Long {
        val n = dao.attachmentsOf(entryId).count { it.kind == "doc" } + 1
        return dao.insertAttachment(
            AttachmentEntity(entryId = entryId, kind = "doc", title = "Документ $n", createdAt = System.currentTimeMillis())
        )
    }

    fun renameAttachment(a: AttachmentEntity, title: String) =
        viewModelScope.launch { dao.updateAttachment(a.copy(title = title.trim().ifBlank { a.title })) }

    fun deleteAttachment(a: AttachmentEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) { deleteAttachmentFile(a) }
        dao.deleteAttachment(a.id)
    }

    fun saveDoc(id: Long, title: String, rich: String, text: String) = viewModelScope.launch {
        dao.attachment(id)?.let {
            if (it.title != title || it.docRich != rich || it.docText != text) {
                dao.updateAttachment(it.copy(title = title.ifBlank { it.title }, docRich = rich, docText = text))
            }
        }
    }

    /** Картинка для вставки в текст документа: копируется в хранилище, возвращается адрес для редактора. */
    suspend fun importInlineImage(uri: Uri): String? =
        withContext(Dispatchers.IO) { runCatching { Media.importImage(app, uri) }.getOrNull() }

    fun mediaFile(a: AttachmentEntity): File = File(Media.dir(app), a.fileName)

    var backupIncludeMedia by mutableStateOf(prefs.getBoolean("bk_media", true))
        private set

    fun setBackupMedia(on: Boolean) {
        backupIncludeMedia = on
        prefs.edit().putBoolean("bk_media", on).apply()
    }

    // ---------- PIN-код ----------
    var pinEnabled by mutableStateOf(prefs.getString("pin_hash", null) != null)
        private set
    var locked by mutableStateOf(prefs.getString("pin_hash", null) != null)
    var lockDelaySec by mutableIntStateOf(prefs.getInt("lock_delay", 60))
        private set
    var secureScreen by mutableStateOf(prefs.getBoolean("secure_screen", false))
        private set

    val pinLength: Int get() = prefs.getInt("pin_len", 4)
    val secretQuestion: String get() = prefs.getString("pin_q", "") ?: ""

    /** Секунд до следующей попытки (0 — можно вводить). */
    fun lockoutSeconds(): Int {
        val left = prefs.getLong("pin_until", 0L) - System.currentTimeMillis()
        return if (left <= 0) 0 else (left / 1000 + 1).toInt()
    }

    private fun registerFail() {
        val fails = prefs.getInt("pin_fail", 0) + 1
        val e = prefs.edit().putInt("pin_fail", fails)
        if (fails % 5 == 0) {
            val wait = 30_000L * (1L shl minOf(fails / 5 - 1, 4)) // 30 с, 1 мин, 2 мин, 4 мин, 8 мин
            e.putLong("pin_until", System.currentTimeMillis() + wait)
        }
        e.apply()
    }

    private fun clearFails() = prefs.edit().putInt("pin_fail", 0).putLong("pin_until", 0L).apply()

    /** true — PIN верный (приложение разблокируется). */
    fun unlock(pin: String): Boolean {
        if (lockoutSeconds() > 0) return false
        val ok = PinLock.matches(pin, prefs.getString("pin_salt", "") ?: "", prefs.getString("pin_hash", "") ?: "")
        if (ok) { clearFails(); locked = false } else registerFail()
        return ok
    }

    fun checkPin(pin: String): Boolean =
        PinLock.matches(pin, prefs.getString("pin_salt", "") ?: "", prefs.getString("pin_hash", "") ?: "")

    fun checkAnswer(answer: String): Boolean {
        if (lockoutSeconds() > 0) return false
        val ok = PinLock.matches(
            PinLock.normalizeAnswer(answer), prefs.getString("ans_salt", "") ?: "", prefs.getString("ans_hash", "") ?: "",
        )
        if (ok) clearFails() else registerFail()
        return ok
    }

    fun setPin(pin: String, question: String? = null, answer: String? = null) {
        val salt = PinLock.newSalt()
        val e = prefs.edit().putString("pin_salt", salt).putString("pin_hash", PinLock.hash(pin, salt)).putInt("pin_len", pin.length)
        if (question != null && answer != null) {
            val aSalt = PinLock.newSalt()
            e.putString("pin_q", question.trim()).putString("ans_salt", aSalt)
                .putString("ans_hash", PinLock.hash(PinLock.normalizeAnswer(answer), aSalt))
        }
        e.putInt("pin_fail", 0).putLong("pin_until", 0L).apply()
        pinEnabled = true
    }

    fun setSecret(question: String, answer: String) {
        val aSalt = PinLock.newSalt()
        prefs.edit().putString("pin_q", question.trim()).putString("ans_salt", aSalt)
            .putString("ans_hash", PinLock.hash(PinLock.normalizeAnswer(answer), aSalt)).apply()
    }

    fun disablePin() {
        prefs.edit().remove("pin_hash").remove("pin_salt").remove("pin_len").remove("pin_q").remove("ans_hash").remove("ans_salt").apply()
        pinEnabled = false
        locked = false
    }

    fun setLockDelay(sec: Int) {
        lockDelaySec = sec
        prefs.edit().putInt("lock_delay", sec).apply()
    }

    fun changeSecureScreen(on: Boolean) {
        secureScreen = on
        prefs.edit().putBoolean("secure_screen", on).apply()
    }

    // ---------- Библиотека ----------
    val books = dao.books().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private suspend fun addBookFile(tmp: File, displayName: String): BookEntity {
        var format = Library.detectFormat(tmp)
        var file = tmp
        if (format == "fb2zip") {
            val fb = File(tmp.parentFile, "${UUID.randomUUID()}.fb2")
            Library.unzipFb2(tmp, fb)
            tmp.delete()
            file = fb
            format = "fb2"
        }
        val target = File(Library.dir(app), "${UUID.randomUUID()}.$format")
        file.renameTo(target)
        val name = displayName.substringBeforeLast('.').ifBlank { "Книга" }
        var title: String? = null
        var author: String? = null
        var total = 0
        try {
            if (format == "pdf") {
                android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(target, android.os.ParcelFileDescriptor.MODE_READ_ONLY))
                    .use { total = it.pageCount }
            } else {
                val (meta, paras) = Library.parse(target, format)
                title = meta.title; author = meta.author; total = paras.size
                java.io.File(target.path + ".cache").bufferedWriter().use { w ->
                    paras.forEach { w.write((if (it.heading) "H\t" else "P\t") + it.text.replace('\n', ' ')); w.newLine() }
                }
            }
        } catch (e: Exception) {
            target.delete()
            File(target.path + ".cache").delete()
            throw IllegalStateException("Не удалось прочитать «$displayName»: ${e.message ?: "неизвестный формат"}")
        }
        val book = BookEntity(
            title = (title ?: name).take(200), author = (author ?: "").take(120), format = format,
            fileName = target.name, sizeBytes = target.length(), addedAt = System.currentTimeMillis(), total = total,
        )
        return book.copy(id = dao.insertBook(book))
    }

    fun importBooks(uris: List<Uri>) = viewModelScope.launch {
        var ok = 0
        for (uri in uris) {
            try {
                withContext(Dispatchers.IO) {
                    val name = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Книга"
                    val tmp = File(Library.dir(app), "import_${UUID.randomUUID()}.tmp")
                    app.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                        ?: error("Не удалось открыть файл")
                    addBookFile(tmp, name)
                }
                ok++
            } catch (e: Exception) {
                messages.emit(e.message ?: "Не удалось добавить книгу")
            }
        }
        if (ok > 0) messages.emit(if (ok == 1) "Книга добавлена" else "Добавлено книг: $ok")
    }

    fun importBookUrl(url: String) = viewModelScope.launch {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            messages.emit("Ссылка должна начинаться с http:// или https://")
            return@launch
        }
        messages.emit("Скачиваем…")
        try {
            withContext(Dispatchers.IO) {
                val tmp = File(Library.dir(app), "import_${UUID.randomUUID()}.tmp")
                tmp.writeBytes(BibleApi.fetchBytes(u))
                addBookFile(tmp, Uri.decode(u.substringAfterLast('/').substringBefore('?')).ifBlank { "Книга" })
            }
            messages.emit("Книга добавлена")
        } catch (e: Exception) {
            messages.emit("Не удалось загрузить: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun deleteBook(b: BookEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            File(Library.dir(app), b.fileName).delete()
            File(Library.dir(app), b.fileName + ".cache").delete()
        }
        dao.deleteBook(b.id)
    }

    fun renameBook(b: BookEntity, title: String, author: String) =
        viewModelScope.launch { dao.updateBook(b.copy(title = title.trim().ifBlank { b.title }, author = author.trim())) }

    suspend fun book(id: Long): BookEntity? = dao.book(id)

    suspend fun loadBook(b: BookEntity): List<Library.Para> =
        withContext(Dispatchers.IO) { Library.load(File(Library.dir(app), b.fileName), b.format) }

    fun bookFile(b: BookEntity): File = File(Library.dir(app), b.fileName)

    fun saveBookProgress(id: Long, index: Int, total: Int) = viewModelScope.launch { dao.saveProgress(id, index, total) }

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
