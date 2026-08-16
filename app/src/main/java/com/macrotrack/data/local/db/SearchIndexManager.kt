package com.macrotrack.data.local.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the FTS5 search index for [com.macrotrack.data.local.db.entity.FoodItemEntity].
 *
 * Room 2.8.4 only ships Fts3/Fts4 annotations, so the index is managed manually
 * via raw SQL instead of a Room entity:
 *
 *  - [FTS_TABLE]: an external-content FTS5 table over `food_items`, tokenized
 *    with `unicode61 remove_diacritics 2` (Unicode-aware, case- and
 *    diacritic-insensitive) and prefix indexes for 2- and 3-character prefix
 *    queries. BM25 ranking weights `name` at 1.0 and `brand` at 0.2.
 *  - [TRIGRAM_TABLE]: an external-content FTS5 table over `food_items` using
 *    the `trigram` tokenizer (`remove_diacritics 1`), which supports substring
 *    matching for typo-tolerant ("fuzzy") candidate retrieval.
 *
 * Both tables are kept in sync with `food_items` by the triggers defined in
 * [TRIGGERS], and can be rebuilt from scratch with [rebuild].
 *
 * If the connected SQLite build lacks the trigram tokenizer or its
 * `remove_diacritics` option (framework SQLite below ~3.42), the trigram table
 * degrades to a plain unicode61 table with the same name so queries never
 * fail; [isTrigramIndexActive] reports which tokenizer is actually in use.
 * The app's bundled SQLite driver (androidx.sqlite:sqlite-bundled) always
 * provides the full trigram tokenizer.
 */
@Singleton
class SearchIndexManager @Inject constructor() {

    /**
     * True when [TRIGRAM_TABLE] is backed by the trigram tokenizer. Set during
     * [createIndexes] / [ensureIndexes]; read it before relying on fuzzy
     * candidate quality. Queries are safe regardless of this flag.
     */
    @Volatile
    var isTrigramIndexActive: Boolean = true
        private set

    /** Minimal executor seam so the index DDL can be run outside Android (e.g. JVM tests). */
    fun interface SqlExecutor {
        fun exec(sql: String)
    }

    /** Query seam so index sync checks can run outside Android (e.g. JVM tests). */
    fun interface SqlQuerier {
        /** Runs [sql] and returns the first column of the first row, or `null`. */
        fun query(sql: String): String?
    }

    /**
     * Creates the FTS5 tables and sync triggers (idempotent; safe to call on
     * every open). Runs inside the Room database callback after Room has
     * created `food_items`.
     */
    fun createIndexes(db: SupportSQLiteDatabase) = createIndexes { sql -> db.execSQL(sql) }

    /**
     * Bundled/framework-driver variant of [createIndexes]. Room 2.8 invokes
     * [androidx.room.RoomDatabase.Callback.onCreate] with an
     * [SQLiteConnection] when the database is opened by a real SQLite driver
     * (e.g. [androidx.sqlite.driver.bundled.BundledSQLiteDriver]), so the index
     * must also be creatable from the raw connection API.
     */
    fun createIndexes(connection: SQLiteConnection) =
        createIndexes(SqlExecutor { sql -> connection.execSQL(sql) })

    /** JVM-testable variant of [createIndexes]. */
    fun createIndexes(executor: SqlExecutor) {
        executor.exec(CREATE_FTS_TABLE)
        createTriggers(executor, FTS_TABLE)
        try {
            executor.exec(CREATE_TRIGRAM_TABLE)
            isTrigramIndexActive = true
        } catch (e: RuntimeException) {
            // Degraded mode: same table name with a unicode61 tokenizer so the
            // fuzzy queries keep working (exact-token matches only).
            executor.exec(CREATE_TRIGRAM_FALLBACK_TABLE)
            isTrigramIndexActive = false
        }
        createTriggers(executor, TRIGRAM_TABLE)
    }

    /**
     * Idempotent repair + capability check, called from the Room callback on
     * every open so a partially-created or restored database self-heals.
     *
     * The index tables use `CREATE ... IF NOT EXISTS`, which never backfills an
     * existing external-content FTS5 table from `food_items`. A table that was
     * dropped and recreated, or restored without its index data, is therefore
     * empty until rebuilt. [ensureIndexes] detects any `food_items` rowid
     * missing from each FTS index and rebuilds only the affected table, so a
     * healthy index is never rebuilt on open.
     */
    fun ensureIndexes(db: SupportSQLiteDatabase) {
        ensureIndexes(
            executor = SqlExecutor { sql -> db.execSQL(sql) },
            querier = SqlQuerier { sql ->
                db.query(sql).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }
        )
    }

    /**
     * Bundled/framework-driver variant of [ensureIndexes]; see
     * [createIndexes] for why the SQLiteConnection overload is needed.
     * Statements are closed via [use] on every path.
     */
    fun ensureIndexes(connection: SQLiteConnection) {
        ensureIndexes(
            executor = SqlExecutor { sql -> connection.execSQL(sql) },
            querier = SqlQuerier { sql ->
                connection.prepare(sql).use { statement ->
                    if (statement.step()) {
                        if (statement.isNull(0)) null else statement.getText(0)
                    } else {
                        null
                    }
                }
            }
        )
    }

    /** JVM-testable variant of [ensureIndexes]. */
    fun ensureIndexes(executor: SqlExecutor, querier: SqlQuerier) {
        createIndexes(executor)
        if (isOutOfSync(querier, FTS_TABLE)) rebuildIndex(executor, FTS_TABLE)
        if (isOutOfSync(querier, TRIGRAM_TABLE)) rebuildIndex(executor, TRIGRAM_TABLE)
        isTrigramIndexActive = trigramActive(querier)
    }

    /**
     * True when [table] diverges from `food_items` in either direction: a
     * `food_items` rowid missing from the index, or an indexed rowid whose
     * content row no longer exists (e.g. a delete that bypassed the trigger).
     *
     * A plain `SELECT rowid FROM $table` on an external-content FTS5 table is
     * answered from the content table (it cannot reveal what is actually in the
     * index), so membership is read from the FTS5 `_docsize` shadow table,
     * which holds exactly one entry per indexed row.
     */
    private fun isOutOfSync(querier: SqlQuerier, table: String): Boolean {
        val missing = querier.query(
            "SELECT count(*) FROM food_items WHERE id NOT IN (SELECT id FROM ${table}_docsize)"
        )?.toLongOrNull() ?: 0L
        val stale = querier.query(
            "SELECT count(*) FROM ${table}_docsize WHERE id NOT IN (SELECT id FROM food_items)"
        )?.toLongOrNull() ?: 0L
        return missing > 0L || stale > 0L
    }

    private fun rebuildIndex(executor: SqlExecutor, table: String) {
        executor.exec("INSERT INTO $table($table) VALUES('rebuild')")
    }

    /** True when the trigram table is backed by the trigram tokenizer. */
    private fun trigramActive(querier: SqlQuerier): Boolean {
        val sql = querier.query(
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '$TRIGRAM_TABLE'"
        ) ?: ""
        // Check the tokenizer itself, not the table name: the degraded fallback
        // reuses the same table name but records a unicode61 tokenizer.
        return sql.contains("tokenize = 'trigram")
    }

    /**
     * Discards both indexes and rebuilds them from the current contents of
     * `food_items`. Used by tests and available for manual repair.
     */
    fun rebuild(db: SupportSQLiteDatabase) = rebuild { sql -> db.execSQL(sql) }

    /** JVM-testable variant of [rebuild]. */
    fun rebuild(executor: SqlExecutor) {
        rebuildIndex(executor, FTS_TABLE)
        rebuildIndex(executor, TRIGRAM_TABLE)
    }

    private fun createTriggers(executor: SqlExecutor, table: String) {
        executor.exec(FTS_TRIGGER_INSERT(table))
        executor.exec(FTS_TRIGGER_DELETE(table))
        executor.exec(FTS_TRIGGER_UPDATE(table))
    }

    companion object {
        const val FTS_TABLE = "food_items_fts"
        const val TRIGRAM_TABLE = "food_items_fts_trigram"

        /**
         * Upper bound on the candidate rows returned by the search DAO
         * queries. The application-level ranker is expected to cap the final
         * result set; this window intentionally exceeds the old limit of 50.
         */
        const val CANDIDATE_WINDOW = 200

        /**
         * BM25 column weights: a hit in `name` is worth 5x a hit in `brand`.
         * Weights map to FTS5 columns left-to-right.
         */
        const val NAME_BM25_WEIGHT = 1.0
        const val BRAND_BM25_WEIGHT = 0.2

        /**
         * Main index: unicode61 (Unicode-aware case folding, full diacritic
         * removal) with prefix indexes for fast `"chi"*`-style queries.
         */
        val CREATE_FTS_TABLE = """
            CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE USING fts5(
                name,
                brand,
                content = 'food_items',
                content_rowid = 'id',
                prefix = '2 3',
                tokenize = 'unicode61 remove_diacritics 2'
            )
        """.trimIndent()

        /**
         * Fuzzy index: trigram tokenizer with diacritic removal, so substring
         * typos ("chickn") and accented text ("café" vs "cafe") both match.
         */
        val CREATE_TRIGRAM_TABLE = """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TRIGRAM_TABLE USING fts5(
                name,
                brand,
                content = 'food_items',
                content_rowid = 'id',
                tokenize = 'trigram remove_diacritics 1'
            )
        """.trimIndent()

        /**
         * Degraded fuzzy index used when the trigram tokenizer is unavailable:
         * same table name, unicode61 tokenizer, so queries cannot break.
         */
        val CREATE_TRIGRAM_FALLBACK_TABLE = """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TRIGRAM_TABLE USING fts5(
                name,
                brand,
                content = 'food_items',
                content_rowid = 'id',
                tokenize = 'unicode61 remove_diacritics 2'
            )
        """.trimIndent()

        private fun FTS_TRIGGER_INSERT(table: String) = """
            CREATE TRIGGER IF NOT EXISTS ${table}_ai AFTER INSERT ON food_items BEGIN
                INSERT INTO $table(rowid, name, brand) VALUES (new.id, new.name, new.brand);
            END
        """.trimIndent()

        private fun FTS_TRIGGER_DELETE(table: String) = """
            CREATE TRIGGER IF NOT EXISTS ${table}_ad AFTER DELETE ON food_items BEGIN
                INSERT INTO $table($table, rowid, name, brand) VALUES ('delete', old.id, old.name, old.brand);
            END
        """.trimIndent()

        private fun FTS_TRIGGER_UPDATE(table: String) = """
            CREATE TRIGGER IF NOT EXISTS ${table}_au AFTER UPDATE ON food_items BEGIN
                INSERT INTO $table($table, rowid, name, brand) VALUES ('delete', old.id, old.name, old.brand);
                INSERT INTO $table(rowid, name, brand) VALUES (new.id, new.name, new.brand);
            END
        """.trimIndent()

        /** The six sync triggers, for tests. */
        val TRIGGERS: List<String> = listOf(
            FTS_TRIGGER_INSERT(FTS_TABLE),
            FTS_TRIGGER_DELETE(FTS_TABLE),
            FTS_TRIGGER_UPDATE(FTS_TABLE),
            FTS_TRIGGER_INSERT(TRIGRAM_TABLE),
            FTS_TRIGGER_DELETE(TRIGRAM_TABLE),
            FTS_TRIGGER_UPDATE(TRIGRAM_TABLE),
        )
    }
}