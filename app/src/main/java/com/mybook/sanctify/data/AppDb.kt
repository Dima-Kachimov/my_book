package com.mybook.sanctify.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TranslationEntity::class, VerseEntity::class, HighlightEntity::class, NoteEntity::class,
        BookmarkEntity::class, SectionEntity::class, EntryEntity::class, EntryVerseEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        /** Разделы дневника, которые есть всегда. */
        val DEFAULT_SECTIONS = listOf("Освящение", "Мысли для проповеди", "Молитвенные нужды")

        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "diary.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_SECTIONS.forEachIndexed { i, name ->
                            db.execSQL("INSERT INTO sections(name, isDefault, sortOrder) VALUES('$name', 1, $i)")
                        }
                    }
                })
                .build()
    }
}
