package com.mybook.sanctify

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mybook.sanctify.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class PrefItem(val key: String, val type: String, val value: String)

/** Содержимое data.json в ZIP-копии: БД, настройки приложения и список книг библиотеки. */
@Serializable
data class FullBackup(
    val version: Int = 2,
    val db: Backup,
    val prefs: List<PrefItem> = emptyList(),
    val books: List<BookEntity> = emptyList(),
)

/**
 * Резервная копия — один ZIP-файл:
 *   data.json            — дневник, заметки, закладки, выделения, теги, заучивание, настройки, PIN-код, список книг;
 *   books/<файл>         — файлы книг библиотеки (по желанию);
 *   bibles/N.tsv         — тексты установленных переводов Библии (по желанию).
 * Старые копии в виде одного JSON тоже восстанавливаются.
 */
object BackupManager {
    private const val PREFIX = "sanctify_backup_"
    private const val KEEP = 5
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Ключи настроек, которые относятся к конкретному устройству и в копию не входят. */
    private val deviceKeys = setOf("backup_tree", "backup_on", "backup_last")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    suspend fun buildDb(dao: AppDao) = Backup(
        sections = dao.listSections(), entries = dao.listEntries(), entryVerses = dao.listEntryVerses(),
        highlights = dao.listHighlights(), notes = dao.listNotes(), bookmarks = dao.listBookmarks(),
        tags = dao.listTags(), verseTags = dao.listVerseTags(), memos = dao.listMemos(),
        attachments = dao.listAttachments(),
    )

    private fun exportPrefs(ctx: Context): List<PrefItem> = prefs(ctx).all.mapNotNull { (k, v) ->
        if (k in deviceKeys) return@mapNotNull null
        when (v) {
            is Boolean -> PrefItem(k, "bool", v.toString())
            is Int -> PrefItem(k, "int", v.toString())
            is Long -> PrefItem(k, "long", v.toString())
            is Float -> PrefItem(k, "float", v.toString())
            is String -> PrefItem(k, "string", v)
            else -> null
        }
    }

    private fun importPrefs(ctx: Context, items: List<PrefItem>) {
        val e = prefs(ctx).edit()
        items.forEach {
            when (it.type) {
                "bool" -> e.putBoolean(it.key, it.value.toBoolean())
                "int" -> it.value.toIntOrNull()?.let { v -> e.putInt(it.key, v) }
                "long" -> it.value.toLongOrNull()?.let { v -> e.putLong(it.key, v) }
                "float" -> it.value.toFloatOrNull()?.let { v -> e.putFloat(it.key, v) }
                "string" -> e.putString(it.key, it.value)
            }
        }
        e.apply()
    }

    /** Записывает полную копию в [out]. */
    suspend fun writeZip(ctx: Context, out: OutputStream, includeBooks: Boolean, includeBibles: Boolean, includeMedia: Boolean = true) {
        val dao = (ctx.applicationContext as App).db.dao()
        val books = if (includeBooks) dao.listBooks() else emptyList()
        val full = FullBackup(db = buildDb(dao), prefs = exportPrefs(ctx), books = books)
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("data.json"))
            z.write(json.encodeToString(FullBackup.serializer(), full).toByteArray())
            z.closeEntry()
            for (b in books) {
                val f = File(Library.dir(ctx), b.fileName)
                if (!f.exists()) continue
                z.putNextEntry(ZipEntry("books/${b.fileName}"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            if (includeMedia) {
                Media.dir(ctx).listFiles()?.filter { it.isFile }?.forEach { f ->
                    z.putNextEntry(ZipEntry("media/${f.name}"))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            if (includeBibles) {
                dao.listTranslationsOnce().forEachIndexed { i, t ->
                    z.putNextEntry(ZipEntry("bibles/$i.tsv"))
                    val w = z.bufferedWriter()
                    w.write("#\t${t.id}\t${t.name.clean()}\t${t.language.clean()}\n")
                    dao.versesOf(t.id).forEach { v -> w.write("${v.book}\t${v.chapter}\t${v.verse}\t${v.text.clean()}\n") }
                    w.flush()
                    z.closeEntry()
                }
            }
        }
    }

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    /** Читает копию: ZIP (новый формат) или JSON (старый). Возвращает число восстановленных Библий. */
    suspend fun restore(ctx: Context, input: InputStream) {
        val db = (ctx.applicationContext as App).db
        val dao = db.dao()
        val bytes = input.buffered()
        bytes.mark(4)
        val magic = ByteArray(2)
        val n = bytes.read(magic)
        bytes.reset()
        if (n == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
            var full: FullBackup? = null
            ZipInputStream(bytes).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        name == "data.json" -> full = json.decodeFromString(FullBackup.serializer(), z.readBytes().toString(Charsets.UTF_8))
                        name.startsWith("books/") && !name.contains("..") -> {
                            val f = File(Library.dir(ctx), name.substringAfter("books/"))
                            f.outputStream().use { z.copyTo(it) }
                        }
                        name.startsWith("media/") -> {
                            val safe = name.substringAfter("media/").substringAfterLast('/')
                            if (safe.isNotBlank()) File(Media.dir(ctx), safe).outputStream().use { z.copyTo(it) }
                        }
                        name.startsWith("bibles/") -> restoreBible(dao, z)
                    }
                    z.closeEntry()
                    e = z.nextEntry
                }
            }
            val f = full ?: error("В файле нет данных (data.json)")
            applyDb(db, f.db, f.books, replaceBooks = true)
            importPrefs(ctx, f.prefs)
        } else {
            val b = json.decodeFromString(Backup.serializer(), bytes.readBytes().toString(Charsets.UTF_8))
            applyDb(db, b, emptyList(), replaceBooks = false)
        }
    }

    private suspend fun restoreBible(dao: AppDao, z: ZipInputStream) {
        val reader = z.bufferedReader()
        val header = reader.readLine()?.split('\t') ?: return
        if (header.size < 4 || header[0] != "#") return
        val id = header[1]
        dao.deleteVerses(id)
        val batch = ArrayList<VerseEntity>(2000)
        reader.lineSequence().forEach { line ->
            val p = line.split('\t', limit = 4)
            if (p.size == 4) {
                val b = p[0].toIntOrNull(); val c = p[1].toIntOrNull(); val v = p[2].toIntOrNull()
                if (b != null && c != null && v != null) batch.add(VerseEntity(id, b, c, v, p[3]))
                if (batch.size >= 2000) { dao.insertVerses(batch.toList()); batch.clear() }
            }
        }
        if (batch.isNotEmpty()) dao.insertVerses(batch.toList())
        dao.upsertTranslation(TranslationEntity(id, header[2], header[3]))
    }

    private suspend fun applyDb(db: AppDb, b: Backup, books: List<BookEntity>, replaceBooks: Boolean) {
        val dao = db.dao()
        db.withTransaction {
            dao.clearEntryVerses(); dao.clearEntries(); dao.clearSections()
            dao.clearAllHighlights(); dao.clearNotes(); dao.clearBookmarks()
            if (b.tags.isNotEmpty()) { dao.clearVerseTags(); dao.clearTags() }
            dao.clearMemos()
            dao.putSections(b.sections); dao.putEntries(b.entries); dao.putEntryVerses(b.entryVerses)
            dao.putHighlights(b.highlights); dao.putNotes(b.notes); dao.putBookmarks(b.bookmarks)
            if (b.tags.isNotEmpty()) { dao.putTags(b.tags); dao.putVerseTags(b.verseTags) }
            dao.putMemos(b.memos)
            dao.putAttachments(b.attachments)
            if (replaceBooks) { dao.clearBooks(); dao.putBooks(books) }
        }
    }

    // ---------- автокопия ----------
    private suspend fun writeToFolder(ctx: Context, tree: Uri, includeBooks: Boolean, includeBibles: Boolean, includeMedia: Boolean) {
        val cr = ctx.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val name = PREFIX + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".zip"
        val doc = DocumentsContract.createDocument(cr, dir, "application/zip", name) ?: error("Не удалось создать файл")
        val out = cr.openOutputStream(doc) ?: error("Не удалось записать файл")
        writeZip(ctx, out, includeBooks, includeBibles, includeMedia)

        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val old = ArrayList<Pair<String, String>>()
        cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(1) ?: continue
                    if (n.startsWith(PREFIX)) old.add(c.getString(0) to n)
                }
            }
        old.sortedByDescending { it.second }.drop(KEEP).forEach {
            try {
                DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, it.first))
            } catch (e: Exception) {
                // не удалось удалить старую копию — не критично
            }
        }
    }

    suspend fun runNow(ctx: Context): Boolean {
        val p = prefs(ctx)
        val tree = p.getString("backup_tree", null)?.let { Uri.parse(it) } ?: return false
        writeToFolder(ctx, tree, p.getBoolean("bk_books", true), p.getBoolean("bk_bibles", true), p.getBoolean("bk_media", true))
        p.edit().putLong("backup_last", System.currentTimeMillis()).apply()
        return true
    }

    fun schedule(ctx: Context, on: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (on) {
            wm.enqueueUniquePeriodicWork(
                "auto_backup", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build(),
            )
        } else {
            wm.cancelUniqueWork("auto_backup")
        }
    }
}

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        BackupManager.runNow(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
