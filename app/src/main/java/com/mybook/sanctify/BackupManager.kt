package com.mybook.sanctify

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mybook.sanctify.data.AppDao
import com.mybook.sanctify.data.Backup
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object BackupManager {
    private const val PREFIX = "sanctify_backup_"
    private const val KEEP = 8
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun buildJson(dao: AppDao): String = json.encodeToString(
        Backup.serializer(),
        Backup(
            sections = dao.listSections(), entries = dao.listEntries(), entryVerses = dao.listEntryVerses(),
            highlights = dao.listHighlights(), notes = dao.listNotes(), bookmarks = dao.listBookmarks(),
            tags = dao.listTags(), verseTags = dao.listVerseTags(), memos = dao.listMemos(),
        ),
    )

    fun parse(text: String): Backup = json.decodeFromString(Backup.serializer(), text)

    /** Пишет копию в выбранную папку (SAF) и оставляет только последние [KEEP] копий. */
    fun writeToFolder(ctx: Context, tree: Uri, text: String) {
        val cr = ctx.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val name = PREFIX + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".json"
        val doc = DocumentsContract.createDocument(cr, dir, "application/json", name) ?: error("Не удалось создать файл")
        cr.openOutputStream(doc)?.use { it.write(text.toByteArray()) } ?: error("Не удалось записать файл")

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
        val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val tree = p.getString("backup_tree", null)?.let { Uri.parse(it) } ?: return false
        writeToFolder(ctx, tree, buildJson((ctx.applicationContext as App).db.dao()))
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
