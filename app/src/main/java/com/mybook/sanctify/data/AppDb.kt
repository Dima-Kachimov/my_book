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
        TagEntity::class, VerseTagEntity::class, MemoEntity::class, BookEntity::class,
    ],
    version = 6,
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

        private fun seedTags(db: SupportSQLiteDatabase) {
            DEFAULT_TAGS.forEachIndexed { i, name ->
                db.execSQL("INSERT INTO tags(name, color) VALUES('$name', ${TAG_COLORS[i % TAG_COLORS.size]})")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `verse_tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tagId` INTEGER NOT NULL, `book` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `verse` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_verse_tags_tagId_book_chapter_verse` ON `verse_tags` (`tagId`, `book`, `chapter`, `verse`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_verse_tags_book_chapter` ON `verse_tags` (`book`, `chapter`)")
                seedTags(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN dueAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memo_verses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`book` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `verseStart` INTEGER NOT NULL, " +
                        "`verseEnd` INTEGER NOT NULL, `box` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_memo_verses_book_chapter_verseStart_verseEnd` ON `memo_verses` (`book`, `chapter`, `verseStart`, `verseEnd`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `author` TEXT NOT NULL, `format` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `lastIndex` INTEGER NOT NULL, `total` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN bodyRich TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "diary.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        seedTags(db)
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
