package com.mybook.sanctify.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Установленный перевод Библии. */
@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val language: String,
)

@Entity(tableName = "verses", primaryKeys = ["translationId", "book", "chapter", "verse"])
data class VerseEntity(
    val translationId: String,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
)

/**
 * Выделение/подчёркивание. startChar = -1 — выделен весь стих (виден во всех переводах),
 * иначе это диапазон символов в тексте перевода [translationId].
 */
@Serializable
@Entity(tableName = "highlights", indices = [Index("book", "chapter")])
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val translationId: String,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val startChar: Int,
    val endChar: Int,
    val color: Int,
    val underline: Boolean,
)

@Serializable
@Entity(tableName = "notes", indices = [Index("book", "chapter")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val label: String = "",
    val createdAt: Long,
)

/** Раздел дневника («Освящение», «Мысли для проповеди», «Молитвенные нужды», свои). */
@Serializable
@Entity(tableName = "sections")
data class SectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    /** true — раздел показывается как простой нумерованный список (1, 2, 3…), например молитвенные нужды. */
    val isList: Boolean = false,
)

@Serializable
@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(
        entity = SectionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sectionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sectionId")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Отметка «выполнено/отвечено» для пунктов списка. */
    val done: Boolean = false,
)

/** Стих(и), прикреплённые к записи дневника. text — снимок текста на момент добавления. */
@Serializable
@Entity(
    tableName = "entry_verses",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("entryId")],
)
data class EntryVerseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val translationId: String,
    val book: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val text: String,
)

/** Цвета тегов (ARGB). */
val TAG_COLORS: List<Int> =
    listOf(0xFFE57373, 0xFFFFB74D, 0xFFFFD54F, 0xFF81C784, 0xFF64B5F6, 0xFFBA68C8, 0xFF4DB6AC, 0xFFF06292)
        .map { it.toInt() }

/** Теги по умолчанию (создаются при установке). */
val DEFAULT_TAGS = listOf("Любовь", "Обетование", "Заповедь", "Молитва", "Вера", "Надежда")

/** Тег для стихов: «любовь», «обетование», «заповедь»… */
@Serializable
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
)

/** Привязка тега к стиху (не зависит от перевода). */
@Serializable
@Entity(
    tableName = "verse_tags",
    foreignKeys = [ForeignKey(
        entity = TagEntity::class,
        parentColumns = ["id"],
        childColumns = ["tagId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["tagId", "book", "chapter", "verse"], unique = true), Index(value = ["book", "chapter"])],
)
data class VerseTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tagId: Long,
    val book: Int,
    val chapter: Int,
    val verse: Int,
)

@Serializable
data class Backup(
    val version: Int = 1,
    val sections: List<SectionEntity>,
    val entries: List<EntryEntity>,
    val entryVerses: List<EntryVerseEntity>,
    val highlights: List<HighlightEntity>,
    val notes: List<NoteEntity>,
    val bookmarks: List<BookmarkEntity>,
    val tags: List<TagEntity> = emptyList(),
    val verseTags: List<VerseTagEntity> = emptyList(),
)
