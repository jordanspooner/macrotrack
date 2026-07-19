package com.macrotrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.macrotrack.data.local.db.entity.SectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY timeOfDay ASC")
    fun getAllSections(): Flow<List<SectionEntity>>

    @Query("SELECT COUNT(*) FROM sections")
    suspend fun count(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: SectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sections: List<SectionEntity>)

    @Update
    suspend fun updateSection(section: SectionEntity)

    @Update
    suspend fun updateAll(sections: List<SectionEntity>)

    @Delete
    suspend fun deleteSection(section: SectionEntity)
}
