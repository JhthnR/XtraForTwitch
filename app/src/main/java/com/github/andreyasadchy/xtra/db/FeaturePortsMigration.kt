package com.github.andreyasadchy.xtra.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Nullable columns leave existing downloads readable and retain their old name-based fallback. */
internal val FEATURE_PORTS_MIGRATION = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN quality_codecs TEXT")
        db.execSQL("ALTER TABLE videos ADD COLUMN quality_bitrate INTEGER")
    }
}
