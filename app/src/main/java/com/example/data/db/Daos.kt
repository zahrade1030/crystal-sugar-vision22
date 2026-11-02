package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceSampleDao {
    @Query("SELECT * FROM reference_samples ORDER BY id ASC")
    fun getAllSamples(): Flow<List<ReferenceSampleEntity>>

    @Query("SELECT * FROM reference_samples ORDER BY id ASC")
    suspend fun getAllSamplesDirect(): List<ReferenceSampleEntity>

    @Query("SELECT * FROM reference_samples WHERE id = :id")
    suspend fun getSampleById(id: Int): ReferenceSampleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(sample: ReferenceSampleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<ReferenceSampleEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sample: ReferenceSampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(samples: List<ReferenceSampleEntity>): List<Long>

    @Query("DELETE FROM reference_samples WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface ReferenceVersionDao {
    @Query("SELECT * FROM reference_versions WHERE sampleId = :sampleId ORDER BY versionNumber DESC")
    fun getVersionsForSample(sampleId: Int): Flow<List<ReferenceVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: ReferenceVersionEntity)

    @Query("DELETE FROM reference_versions WHERE versionId = :versionId")
    suspend fun deleteVersion(versionId: Long)
}

@Dao
interface AnalysisHistoryDao {
    @Query("SELECT * FROM analysis_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<AnalysisHistoryEntity>>

    @Query("SELECT * FROM analysis_history WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<AnalysisHistoryEntity>>

    @Query("SELECT * FROM analysis_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): AnalysisHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AnalysisHistoryEntity): Long

    @Query("UPDATE analysis_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM analysis_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM analysis_history")
    suspend fun deleteAll()
}

@Dao
interface CalibrationProfileDao {
    @Query("SELECT * FROM calibration_profiles ORDER BY timestamp DESC")
    fun getAllProfiles(): Flow<List<CalibrationProfileEntity>>

    @Query("SELECT * FROM calibration_profiles ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestProfile(): CalibrationProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: CalibrationProfileEntity): Long

    @Query("DELETE FROM calibration_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
