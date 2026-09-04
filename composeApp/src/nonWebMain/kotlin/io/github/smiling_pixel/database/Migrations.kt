package io.github.smiling_pixel.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Preserves existing Moments while adding media metadata and entry relationships. */
val migration4To5 =
    object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE RoomFileMetadata ADD COLUMN mimeType TEXT NOT NULL " +
                    "DEFAULT 'application/octet-stream'",
            )
            connection.execSQL(
                "ALTER TABLE RoomFileMetadata ADD COLUMN sizeBytes INTEGER NOT NULL DEFAULT -1",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS RoomMomentEntryLink (" +
                    "fileId INTEGER NOT NULL, entrySyncId TEXT NOT NULL, " +
                    "PRIMARY KEY(fileId, entrySyncId), " +
                    "FOREIGN KEY(fileId) REFERENCES RoomFileMetadata(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(entrySyncId) REFERENCES RoomDiaryEntry(syncId) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_RoomMomentEntryLink_fileId " +
                    "ON RoomMomentEntryLink(fileId)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_RoomMomentEntryLink_entrySyncId " +
                    "ON RoomMomentEntryLink(entrySyncId)",
            )
        }
    }
