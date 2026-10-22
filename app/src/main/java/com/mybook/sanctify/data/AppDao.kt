package com.mybook.sanctify.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // ---------- Библия ----------
    @Query("SELECT * FROM translations ORDER BY name")
    fun translations(): Flow<List<TranslationEntity>>

    @Insert(onConflict = REPLACE)
    suspend fun upsertTranslation(t: TranslationEntity)

    @Insert(onConflict = REPLACE)
    suspend fun insertVerses(v: List<VerseEntity>)

    @Query("DELETE FROM verses WHERE translationId = :id")
    suspend fun deleteVerses(id: String)

    @Query("DELETE FROM translations WHERE id = :id")
    suspend fun deleteTranslation(id: String)

    @Query("SELECT * FROM verses WHERE translationId = :t AND book = :b AND chapter = :c ORDER BY verse")
    fun chapter(t: String, b: Int, c: Int): Flow<List<VerseEntity>>

    @Query("SELECT MAX(chapter) FROM verses WHERE translationId = :t AND book = :b")
    suspend fun chapterCount(t: String, b: Int): Int?

    @Query(
        "SELECT * FROM verses WHERE translationId = :t AND book = :b AND chapter = :c " +
            "AND verse BETWEEN :from AND :to ORDER BY verse"
    )
    suspend fun range(t: String, b: Int, c: Int, from: Int, to: Int): List<VerseEntity>

    @Query(
        "SELECT * FROM verses WHERE translationId = :t AND book BETWEEN :from AND :to " +
            "ORDER BY book, chapter, verse"
    )
    suspend fun versesInBooks(t: String, from: Int, to: Int): List<VerseEntity>

    // SQLite LIKE регистронезависим только для ASCII, поэтому передаём варианты регистра.
    @Query(
        "SELECT * FROM verses WHERE translationId = :t AND (text LIKE '%' || :q1 || '%' " +
            "OR text LIKE '%' || :q2 || '%' OR text LIKE '%' || :q3 || '%') " +
            "ORDER BY book, chapter, verse LIMIT :limit"
    )
    suspend fun search(t: String, q1: String, q2: String, q3: String, limit: Int): List<VerseEntity>

    // ---------- Выделения ----------
    @Query("SELECT * FROM highlights WHERE book = :b AND chapter = :c")
    fun highlights(b: Int, c: Int): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY book, chapter, verse")
    fun allHighlights(): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insertHighlight(h: HighlightEntity)

    @Query(
        "DELETE FROM highlights WHERE book = :b AND chapter = :c AND verse IN (:verses) " +
            "AND startChar < 0 AND underline = :underline"
    )
    suspend fun clearWholeVerse(b: Int, c: Int, verses: List<Int>, underline: Boolean)

    @Query("DELETE FROM highlights WHERE book = :b AND chapter = :c AND verse IN (:verses)")
    suspend fun clearHighlights(b: Int, c: Int, verses: List<Int>)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlight(id: Long)

    // ---------- Заметки ----------
    @Query("SELECT * FROM notes WHERE book = :b AND chapter = :c ORDER BY verseStart")
    fun notes(b: Int, c: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY book, chapter, verseStart")
    fun allNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = REPLACE)
    suspend fun upsertNote(n: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    // ---------- Закладки ----------
    @Query("SELECT * FROM bookmarks WHERE book = :b AND chapter = :c")
    fun bookmarks(b: Int, c: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY book, chapter, verse")
    fun allBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE book = :b AND chapter = :c AND verse = :v LIMIT 1")
    suspend fun findBookmark(b: Int, c: Int, v: Int): BookmarkEntity?

    @Insert
    suspend fun insertBookmark(b: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    // ---------- Дневник ----------
    @Query("SELECT * FROM sections ORDER BY sortOrder, id")
    fun sections(): Flow<List<SectionEntity>>

    @Insert
    suspend fun insertSection(s: SectionEntity): Long

    @Update
    suspend fun updateSection(s: SectionEntity)

    @Delete
    suspend fun deleteSection(s: SectionEntity)

    @Query("SELECT * FROM entries WHERE sectionId = :sectionId ORDER BY updatedAt DESC")
    fun entries(sectionId: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: Long): EntryEntity?

    @Insert
    suspend fun insertEntry(e: EntryEntity): Long

    @Update
    suspend fun updateEntry(e: EntryEntity)

    @Query("UPDATE entries SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE entries SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, now: Long)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM entry_verses WHERE entryId = :entryId ORDER BY id")
    fun entryVerses(entryId: Long): Flow<List<EntryVerseEntity>>

    @Insert
    suspend fun insertEntryVerse(v: EntryVerseEntity)

    @Query("DELETE FROM entry_verses WHERE id = :id")
    suspend fun deleteEntryVerse(id: Long)

    // ---------- Теги ----------
    @Query("SELECT * FROM tags ORDER BY name")
    fun tags(): Flow<List<TagEntity>>

    @Insert
    suspend fun insertTag(t: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Query("SELECT * FROM verse_tags WHERE book = :b AND chapter = :c")
    fun verseTags(b: Int, c: Int): Flow<List<VerseTagEntity>>

    @Query("SELECT * FROM verse_tags ORDER BY book, chapter, verse")
    fun allVerseTags(): Flow<List<VerseTagEntity>>

    @Insert(onConflict = IGNORE)
    suspend fun insertVerseTags(l: List<VerseTagEntity>)

    @Query("DELETE FROM verse_tags WHERE tagId = :tagId AND book = :b AND chapter = :c AND verse IN (:verses)")
    suspend fun removeVerseTags(tagId: Long, b: Int, c: Int, verses: List<Int>)

    @Query("SELECT * FROM tags") suspend fun listTags(): List<TagEntity>
    @Query("SELECT * FROM verse_tags") suspend fun listVerseTags(): List<VerseTagEntity>
    @Query("DELETE FROM verse_tags") suspend fun clearVerseTags()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Insert(onConflict = REPLACE) suspend fun putTags(l: List<TagEntity>)
    @Insert(onConflict = REPLACE) suspend fun putVerseTags(l: List<VerseTagEntity>)

    // ---------- Заучивание ----------
    @Query("SELECT * FROM memo_verses ORDER BY dueAt")
    fun memos(): Flow<List<MemoEntity>>

    @Insert(onConflict = IGNORE)
    suspend fun insertMemos(l: List<MemoEntity>)

    @Update
    suspend fun updateMemo(m: MemoEntity)

    @Query("DELETE FROM memo_verses WHERE id = :id")
    suspend fun deleteMemo(id: Long)

    @Query("SELECT * FROM memo_verses") suspend fun listMemos(): List<MemoEntity>
    @Query("DELETE FROM memo_verses") suspend fun clearMemos()
    @Insert(onConflict = REPLACE) suspend fun putMemos(l: List<MemoEntity>)

    // ---------- Молитвы, сроки, «Сегодня» ----------
    @Query("UPDATE entries SET dueAt = :dueAt WHERE id = :id")
    suspend fun setDue(id: Long, dueAt: Long)

    @Query(
        "SELECT e.* FROM entries e JOIN sections s ON e.sectionId = s.id WHERE s.isList = 1 AND e.done = 0 " +
            "ORDER BY CASE WHEN e.dueAt > 0 THEN 0 ELSE 1 END, e.dueAt, e.createdAt"
    )
    fun openPrayers(): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entries e JOIN sections s ON e.sectionId = s.id WHERE s.isList = 1 AND e.done = 0")
    suspend fun openPrayersCount(): Int

    @Query("SELECT * FROM entries WHERE dueAt > :now AND done = 0")
    suspend fun upcomingDue(now: Long): List<EntryEntity>

    @Query("SELECT * FROM entries ORDER BY updatedAt DESC LIMIT 1")
    fun latestEntry(): Flow<EntryEntity?>

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun section(id: Long): SectionEntity?

    // ---------- Вложения ----------
    @Query("SELECT * FROM attachments WHERE entryId = :entryId ORDER BY id")
    fun attachments(entryId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE entryId = :entryId")
    suspend fun attachmentsOf(entryId: Long): List<AttachmentEntity>

    @Query("SELECT a.* FROM attachments a JOIN entries e ON a.entryId = e.id WHERE e.sectionId = :sectionId")
    suspend fun attachmentsInSection(sectionId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun attachment(id: Long): AttachmentEntity?

    @Insert
    suspend fun insertAttachment(a: AttachmentEntity): Long

    @Update
    suspend fun updateAttachment(a: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)

    @Query("SELECT * FROM attachments") suspend fun listAttachments(): List<AttachmentEntity>
    @Insert(onConflict = REPLACE) suspend fun putAttachments(l: List<AttachmentEntity>)

    // ---------- Библиотека ----------
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun books(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun book(id: Long): BookEntity?

    @Insert
    suspend fun insertBook(b: BookEntity): Long

    @Update
    suspend fun updateBook(b: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    @Query("UPDATE books SET lastIndex = :idx, total = :total WHERE id = :id")
    suspend fun saveProgress(id: Long, idx: Int, total: Int)

    @Query("SELECT * FROM books") suspend fun listBooks(): List<BookEntity>
    @Query("DELETE FROM books") suspend fun clearBooks()
    @Insert(onConflict = REPLACE) suspend fun putBooks(l: List<BookEntity>)

    @Query("SELECT * FROM translations") suspend fun listTranslationsOnce(): List<TranslationEntity>

    @Query("SELECT * FROM verses WHERE translationId = :id ORDER BY book, chapter, verse")
    suspend fun versesOf(id: String): List<VerseEntity>

    // ---------- Резервная копия ----------
    @Query("SELECT * FROM sections") suspend fun listSections(): List<SectionEntity>
    @Query("SELECT * FROM entries") suspend fun listEntries(): List<EntryEntity>
    @Query("SELECT * FROM entry_verses") suspend fun listEntryVerses(): List<EntryVerseEntity>
    @Query("SELECT * FROM highlights") suspend fun listHighlights(): List<HighlightEntity>
    @Query("SELECT * FROM notes") suspend fun listNotes(): List<NoteEntity>
    @Query("SELECT * FROM bookmarks") suspend fun listBookmarks(): List<BookmarkEntity>

    @Query("DELETE FROM entry_verses") suspend fun clearEntryVerses()
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM sections") suspend fun clearSections()
    @Query("DELETE FROM highlights") suspend fun clearAllHighlights()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM bookmarks") suspend fun clearBookmarks()

    @Insert(onConflict = REPLACE) suspend fun putSections(l: List<SectionEntity>)
    @Insert(onConflict = REPLACE) suspend fun putEntries(l: List<EntryEntity>)
    @Insert(onConflict = REPLACE) suspend fun putEntryVerses(l: List<EntryVerseEntity>)
    @Insert(onConflict = REPLACE) suspend fun putHighlights(l: List<HighlightEntity>)
    @Insert(onConflict = REPLACE) suspend fun putNotes(l: List<NoteEntity>)
    @Insert(onConflict = REPLACE) suspend fun putBookmarks(l: List<BookmarkEntity>)
}
