package com.mybook.sanctify.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TranslationEntity::class, VerseEntity::class, HighlightEntity::class, NoteEntity::class,
        BookmarkEntity::class, SectionEntity::class, EntryEntity::class, EntryVerseEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        /** Разделы дневника, которые есть всегда: название и режим «нумерованный список». */
        val DEFAULT_SECTIONS = listOf("Освящение" to false, "Мысли для проповеди" to false, "Молитвенные нужды" to true)

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sections ADD COLUMN isList INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN done INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sections SET isList = 1 WHERE isDefault = 1 AND name = 'Молитвенные нужды'")
            }
        }

        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "diary.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_SECTIONS.forEachIndexed { i, (name, isList) ->
                            db.execSQL(
                                "INSERT INTO sections(name, isDefault, sortOrder, isList) " +
                                    "VALUES('$name', 1, $i, ${if (isList) 1 else 0})"
                            )
                        }
                    }
                })
                .build()
    }
}
