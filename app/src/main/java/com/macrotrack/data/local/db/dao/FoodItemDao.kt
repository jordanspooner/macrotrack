package com.macrotrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.SkipQueryVerification
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.macrotrack.data.local.db.SearchIndexManager
import com.macrotrack.data.local.db.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
// The FTS5 tables (food_items_fts, food_items_fts_trigram) are created by
// SearchIndexManager via raw SQL, so they are not Room entities; Room's
// compile-time query verifier therefore cannot resolve them. They also cannot
// be listed as invalidation tables of @Query Flow methods: InvalidationTracker
// only knows Room schema tables and throws "There is no table with name ..."
// when the Flow starts. The FTS search methods below are therefore @RawQuery
// observed on food_items only; the manual FTS5 triggers keep the index in sync.
@SkipQueryVerification
interface FoodItemDao {
    /**
     * FTS5 text search over the main (unicode61) index, ordered by BM25 with
     * name hits weighted above brand hits. [query] is the FTS5 MATCH string
     * (e.g. `"chi"* "bre"*`); it is bound as a parameter, never interpolated.
     *
     * Returns up to [SearchIndexManager.CANDIDATE_WINDOW] candidates; the
     * application ranker caps the final result set.
     */
    fun searchFoods(query: String): Flow<List<FoodItemEntity>> =
        searchFoodsRaw(SimpleSQLiteQuery(SEARCH_FTS_SQL, arrayOf(query, query)))

    /**
     * @RawQuery implementation of [searchFoods]. [query] must select
     * `food_items.*` joined against the main FTS5 index on rowid with the
     * MATCH value bound as a parameter (see [SEARCH_FTS_SQL]).
     */
    @RawQuery(observedEntities = [FoodItemEntity::class])
    fun searchFoodsRaw(query: SupportSQLiteQuery): Flow<List<FoodItemEntity>>

    /**
     * FTS5 trigram (fuzzy) search over the fuzzy index, ordered by BM25.
     * [query] is a MATCH string of quoted normalized tokens (see
     * FuzzyQueryFormatter); it is bound as a parameter, never interpolated.
     */
    fun searchFoodsFuzzy(query: String): Flow<List<FoodItemEntity>> =
        searchFoodsFuzzyRaw(SimpleSQLiteQuery(SEARCH_TRIGRAM_SQL, arrayOf(query, query)))

    /**
     * @RawQuery implementation of [searchFoodsFuzzy]. [query] must select
     * `food_items.*` joined against the trigram FTS5 index on rowid with the
     * MATCH value bound as a parameter (see [SEARCH_TRIGRAM_SQL]).
     */
    @RawQuery(observedEntities = [FoodItemEntity::class])
    fun searchFoodsFuzzyRaw(query: SupportSQLiteQuery): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE source = 'USER'")
    fun getAllUserFoods(): Flow<List<FoodItemEntity>>

    /** FTS5 text search restricted to user foods; same contract as [searchFoods]. */
    fun searchUserFoods(query: String): Flow<List<FoodItemEntity>> =
        searchUserFoodsRaw(SimpleSQLiteQuery(SEARCH_USER_FTS_SQL, arrayOf(query, query)))

    /**
     * @RawQuery implementation of [searchUserFoods]. [query] must select
     * `food_items.*` joined against the main FTS5 index on rowid, restricted
     * to `source = 'USER'`, with the MATCH value bound as a parameter
     * (see [SEARCH_USER_FTS_SQL]).
     */
    @RawQuery(observedEntities = [FoodItemEntity::class])
    fun searchUserFoodsRaw(query: SupportSQLiteQuery): Flow<List<FoodItemEntity>>

    /**
     * FTS5 trigram (fuzzy) search restricted to user foods; same contract as
     * [searchFoodsFuzzy].
     */
    fun searchUserFoodsFuzzy(query: String): Flow<List<FoodItemEntity>> =
        searchUserFoodsFuzzyRaw(SimpleSQLiteQuery(SEARCH_USER_TRIGRAM_SQL, arrayOf(query, query)))

    /**
     * @RawQuery implementation of [searchUserFoodsFuzzy]. [query] must select
     * `food_items.*` joined against the trigram FTS5 index on rowid, restricted
     * to `source = 'USER'`, with the MATCH value bound as a parameter
     * (see [SEARCH_USER_TRIGRAM_SQL]).
     */
    @RawQuery(observedEntities = [FoodItemEntity::class])
    fun searchUserFoodsFuzzyRaw(query: SupportSQLiteQuery): Flow<List<FoodItemEntity>>

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM food_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM food_items WHERE source = 'USER'")
    suspend fun countUserFoods(): Int

    @Query("SELECT COUNT(*) FROM food_items WHERE dataSourceId = :sourceId AND source != 'USER'")
    suspend fun countByDataSource(sourceId: String): Int

    @Query("DELETE FROM food_items WHERE dataSourceId = :sourceId")
    suspend fun deleteByDataSource(sourceId: String)

    @Query("DELETE FROM food_items")
    suspend fun clearAll()

    @Query("SELECT * FROM food_items WHERE ean = :ean LIMIT 1")
    suspend fun getFoodByEan(ean: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getFoodById(id: Long): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodItemEntity): Long

    @Update
    suspend fun update(food: FoodItemEntity)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    companion object {
        /**
         * FTS5 search over the main unicode61 index
         * ([SearchIndexManager.FTS_TABLE]), ordered by BM25 with name hits
         * weighted above brand hits. Both `?` placeholders are bound to the
         * same MATCH value; the candidate window caps the result set.
         */
        val SEARCH_FTS_SQL: String = """
            SELECT food_items.* FROM food_items
            JOIN ${SearchIndexManager.FTS_TABLE} ON food_items.id = ${SearchIndexManager.FTS_TABLE}.rowid
            WHERE ? <> '' AND ${SearchIndexManager.FTS_TABLE} MATCH ?
            ORDER BY bm25(${SearchIndexManager.FTS_TABLE}, ${SearchIndexManager.NAME_BM25_WEIGHT}, ${SearchIndexManager.BRAND_BM25_WEIGHT}), food_items.id
            LIMIT ${SearchIndexManager.CANDIDATE_WINDOW}
        """.trimIndent()

        /**
         * FTS5 trigram (fuzzy) search over [SearchIndexManager.TRIGRAM_TABLE],
         * same shape as [SEARCH_FTS_SQL].
         */
        val SEARCH_TRIGRAM_SQL: String = """
            SELECT food_items.* FROM food_items
            JOIN ${SearchIndexManager.TRIGRAM_TABLE} ON food_items.id = ${SearchIndexManager.TRIGRAM_TABLE}.rowid
            WHERE ? <> '' AND ${SearchIndexManager.TRIGRAM_TABLE} MATCH ?
            ORDER BY bm25(${SearchIndexManager.TRIGRAM_TABLE}, ${SearchIndexManager.NAME_BM25_WEIGHT}, ${SearchIndexManager.BRAND_BM25_WEIGHT}), food_items.id
            LIMIT ${SearchIndexManager.CANDIDATE_WINDOW}
        """.trimIndent()

        /** [SEARCH_FTS_SQL] restricted to `food_items.source = 'USER'`. */
        val SEARCH_USER_FTS_SQL: String = """
            SELECT food_items.* FROM food_items
            JOIN ${SearchIndexManager.FTS_TABLE} ON food_items.id = ${SearchIndexManager.FTS_TABLE}.rowid
            WHERE ? <> '' AND ${SearchIndexManager.FTS_TABLE} MATCH ? AND food_items.source = 'USER'
            ORDER BY bm25(${SearchIndexManager.FTS_TABLE}, ${SearchIndexManager.NAME_BM25_WEIGHT}, ${SearchIndexManager.BRAND_BM25_WEIGHT}), food_items.id
            LIMIT ${SearchIndexManager.CANDIDATE_WINDOW}
        """.trimIndent()

        /** [SEARCH_TRIGRAM_SQL] restricted to `food_items.source = 'USER'`. */
        val SEARCH_USER_TRIGRAM_SQL: String = """
            SELECT food_items.* FROM food_items
            JOIN ${SearchIndexManager.TRIGRAM_TABLE} ON food_items.id = ${SearchIndexManager.TRIGRAM_TABLE}.rowid
            WHERE ? <> '' AND ${SearchIndexManager.TRIGRAM_TABLE} MATCH ? AND food_items.source = 'USER'
            ORDER BY bm25(${SearchIndexManager.TRIGRAM_TABLE}, ${SearchIndexManager.NAME_BM25_WEIGHT}, ${SearchIndexManager.BRAND_BM25_WEIGHT}), food_items.id
            LIMIT ${SearchIndexManager.CANDIDATE_WINDOW}
        """.trimIndent()
    }
}
