package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.model.RoomDiaryEntry
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DiaryDaoImpl(private val roomDao: DiaryRoomDao) : IDiaryDao {
    override val entriesFlow: Flow<List<DiaryEntry>> = roomDao.getAllFlow().map { list ->
        list.map { re ->
            DiaryEntry(
                re.id,
                re.title,
                re.content,
                createdAt = Instant.fromEpochMilliseconds(re.createdAt),
                updatedAt = Instant.fromEpochMilliseconds(re.updatedAt),
            )
        }
    }

    override suspend fun getAll(): List<DiaryEntry> = roomDao.getAll().map { re ->
        DiaryEntry(
            re.id,
            re.title,
            re.content,
            createdAt = Instant.fromEpochMilliseconds(re.createdAt),
            updatedAt = Instant.fromEpochMilliseconds(re.updatedAt),
        )
    }

    override suspend fun insert(entry: DiaryEntry) {
        // Insert using Room; Room will emit updated Flow with assigned id.
        // convert Instant timestamps to epoch millis for Room
        roomDao.insert(
            RoomDiaryEntry(
                entry.id,
                entry.title,
                entry.content,
                createdAt = entry.createdAt.toEpochMilliseconds(),
                updatedAt = entry.updatedAt.toEpochMilliseconds(),
            )
        )
    }

    override suspend fun delete(entry: DiaryEntry) {
        roomDao.delete(
            RoomDiaryEntry(
                entry.id,
                entry.title,
                entry.content,
                createdAt = entry.createdAt.toEpochMilliseconds(),
                updatedAt = entry.updatedAt.toEpochMilliseconds(),
            )
        )
    }
}
