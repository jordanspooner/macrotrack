package com.macrotrack.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "log_entries",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodItemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["date"]),
        Index(value = ["sectionId"]),
        Index(value = ["foodItemId"])
    ]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // Stored as ISO string YYYY-MM-DD
    val sectionId: Long,
    val foodItemId: Long? = null,
    val name: String,
    val brand: String? = null,
    val portionG: Float,
    val portionLabel: String? = null,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val sortOrder: Int,
    val createdAt: Long, // Epoch millis
)
