package com.macrotrack.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SQL-level tests for the FTS5 search index, run against the real SQLite
 * engine (org.xerial sqlite-jdbc, which bundles FTS5 with the trigram
 * tokenizer) using the exact DDL and trigger SQL from [SearchIndexManager].
 *
 * The queries under test mirror the FoodItemDao queries: external-content
 * tables joined on rowid, BM25 ordering with name/brand weights, and MATCH
 * arguments bound as parameters.
 */
class FoodSearchIndexSqlTest {

    private lateinit var connection: Connection
    private lateinit var indexManager: SearchIndexManager

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        indexManager = SearchIndexManager()
        createContentTable()
        indexManager.createIndexes(executor())
    }

    @After
    fun tearDown() {
        connection.close()
    }

    private fun executor(): SearchIndexManager.SqlExecutor = SearchIndexManager.SqlExecutor { sql ->
        connection.createStatement().use { it.execute(sql) }
    }

    private fun querier(): SearchIndexManager.SqlQuerier = SearchIndexManager.SqlQuerier { sql ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { cursor ->
                if (cursor.next()) cursor.getString(1) else null
            }
        }
    }

    private fun createContentTable() {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE food_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source TEXT NOT NULL,
                    sourceId TEXT,
                    dataSourceId TEXT,
                    ean TEXT,
                    brand TEXT,
                    name TEXT NOT NULL,
                    defaultPortionG REAL,
                    defaultPortionLabel TEXT,
                    kcalPer100g REAL NOT NULL,
                    proteinPer100g REAL NOT NULL,
                    carbsPer100g REAL NOT NULL,
                    fatPer100g REAL NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun insertFood(name: String, brand: String? = null): Long {
        connection.prepareStatement(
            """
            INSERT INTO food_items(source, brand, name, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g)
            VALUES ('OPEN_FOOD_FACTS', ?, ?, 100.0, 10.0, 10.0, 5.0)
            """
        ).use { statement ->
            statement.setString(1, brand)
            statement.setString(2, name)
            statement.executeUpdate()
        }
        return connection.createStatement().executeQuery("SELECT last_insert_rowid()").getLong(1)
    }

    private fun searchIds(table: String, query: String, limit: Int = 200): List<Long> {
        connection.prepareStatement(
            """
            SELECT food_items.id FROM food_items
            JOIN $table ON food_items.id = $table.rowid
            WHERE ? <> '' AND $table MATCH ?
            ORDER BY bm25($table, 1.0, 0.2), food_items.id
            LIMIT $limit
            """
        ).use { statement ->
            statement.setString(1, query)
            statement.setString(2, query)
            statement.executeQuery().use { cursor ->
                val ids = mutableListOf<Long>()
                while (cursor.next()) ids.add(cursor.getLong(1))
                return ids
            }
        }
    }

    @Test
    fun `fresh index creation is deterministic and idempotent`() {
        indexManager.createIndexes(executor())
        val names = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { cursor ->
                buildList {
                    while (cursor.next()) add(cursor.getString(1))
                }
            }
        }
        assertTrue(names.contains(SearchIndexManager.FTS_TABLE))
        assertTrue(names.contains(SearchIndexManager.TRIGRAM_TABLE))
        assertTrue(indexManager.isTrigramIndexActive)
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "anything").isEmpty())
    }

    @Test
    fun `trigram tokenizer is genuinely active`() {
        val sql = connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT sql FROM sqlite_master WHERE name = '${SearchIndexManager.TRIGRAM_TABLE}'"
            ).getString(1)
        }
        // Check the tokenizer itself, not the table name (the fallback unicode61
        // table reuses the name food_items_fts_trigram).
        assertTrue(sql.contains("tokenize = 'trigram"))
    }

    @Test
    fun `inserts are indexed via triggers`() {
        val id = insertFood("Chicken breast", "Sainsbury's")
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken").contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chicken\"").contains(id))
    }

    @Test
    fun `updates resync both indexes`() {
        val id = insertFood("Chicken breast")
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken").contains(id))

        connection.createStatement().use { statement ->
            statement.executeUpdate("UPDATE food_items SET name = 'Turkey thigh' WHERE id = $id")
        }
        assertFalse(searchIds(SearchIndexManager.FTS_TABLE, "chicken").contains(id))
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "turkey").contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"turk\"").contains(id))
    }

    @Test
    fun `deletes remove rows from both indexes`() {
        val id = insertFood("Chicken breast")
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM food_items WHERE id = $id")
        }
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken").isEmpty())
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chicken\"").isEmpty())
    }

    @Test
    fun `bm25 ranks name matches above brand-only matches`() {
        val nameMatch = insertFood("Dairy Milk", "Cadbury")
        val brandMatch = insertFood("Crunchy Bar", "Dairy Queen")
        val order = searchIds(SearchIndexManager.FTS_TABLE, "dairy")
        assertEquals(listOf(nameMatch, brandMatch), order)
        // Same ordering through the fuzzy index.
        val fuzzyOrder = searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"dairy\"")
        assertEquals(listOf(nameMatch, brandMatch), fuzzyOrder)
    }

    @Test
    fun `prefix queries use the prefix index and work for quoted prefixes`() {
        val id = insertFood("Chicken breast")
        // Raw prefix token (parent agent's legacy format).
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chic*").contains(id))
        // Quoted prefix token, e.g. QueryNormalizer.ftsPrefixQuery output ("chic"*).
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "\"chic\"*").contains(id))
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "\"chi\"* \"bre\"*").contains(id))
        // Bare prefix tokens, exactly as SearchFoodUseCase/SearchUserFoodsUseCase format them.
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chi* bre*").contains(id))
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken*").contains(id))
    }

    @Test
    fun `trigram matches substrings exactly`() {
        val id = insertFood("Chicken breast", "Tesco")
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chick\"").contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"breast\"").contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"tesco\"").contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chicken\" \"breast\"").contains(id))
        // A typo is NOT a substring: raw trigram matching alone cannot match it.
        assertFalse(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chickn\"").contains(id))
        assertFalse(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"brest\"").contains(id))
    }

    @Test
    fun `formatter trigram groups recover typo candidates via the trigram index`() {
        val id = insertFood("Chicken breast", "Tesco")
        val fuzzyQuery = FuzzyQueryFormatter.format("chickn brest")
        assertEquals(
            "(\"chi\" OR \"hic\" OR \"ick\" OR \"ckn\") AND (\"bre\" OR \"res\" OR \"est\")",
            fuzzyQuery
        )
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, fuzzyQuery!!).contains(id))
        // Longer typos still share leading trigrams with the indexed text.
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, FuzzyQueryFormatter.format("chickenn")!!).contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, FuzzyQueryFormatter.format("chickn tesco")!!).contains(id))
    }

    @Test
    fun `middle typo shares interior trigrams`() {
        val mango = insertFood("Mango")
        val tango = insertFood("Tango")
        val fuzzyQuery = FuzzyQueryFormatter.format("mango")!!
        assertEquals("(\"man\" OR \"ang\" OR \"ngo\")", fuzzyQuery)
        // Both share the "ang"/"ngo" interior trigrams, so a single middle typo
        // still surfaces the candidate; the Kotlin ranker applies edit distance.
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, fuzzyQuery).contains(mango))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, fuzzyQuery).contains(tango))
        // The raw typo is not itself a substring of "Tango".
        assertFalse(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"mango\"").contains(tango))
    }

    @Test
    fun `trigram matching is ascii case insensitive`() {
        val id = insertFood("Chicken breast")
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"CHICKEN\"").contains(id))
    }

    @Test
    fun `unicode61 folds case and diacritics`() {
        val id = insertFood("Café au Lait")
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "cafe").contains(id))
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "CAFÉ").contains(id))
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "\"café\"*").contains(id))
    }

    @Test
    fun `trigram with diacritic removal matches accent-insensitively`() {
        val id = insertFood("Café au Lait")
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"cafe\"").contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"lait\"").contains(id))
    }

    @Test
    fun `candidate window caps results at 200`() {
        for (i in 0 until 201) insertFood("Bulk Food $i")
        assertEquals(200, searchIds(SearchIndexManager.FTS_TABLE, "bulk").size)
        assertEquals(200, searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"bulk\"").size)
    }

    @Test
    fun `blank query returns nothing instead of erroring`() {
        insertFood("Chicken breast")
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "").isEmpty())
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "").isEmpty())
    }

    @Test
    fun `query text is bound and cannot escape the MATCH parameter`() {
        insertFood("Chicken breast")
        // A quote injected into the query must surface as an FTS5 syntax error
        // (proving the value reached the MATCH parser as data, not SQL), and
        // must never return unrelated rows.
        try {
            val result = searchIds(SearchIndexManager.FTS_TABLE, "chicken\" OR 1=1 --")
            assertTrue(result.isEmpty())
        } catch (expected: org.sqlite.SQLiteException) {
            // Acceptable: FTS5 rejects the malformed phrase.
        }
    }

    @Test
    fun `rebuild repopulates both indexes on demand`() {
        val id = insertFood("Chicken breast")
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE ${SearchIndexManager.FTS_TABLE}")
            statement.execute("DROP TABLE ${SearchIndexManager.TRIGRAM_TABLE}")
        }
        // Dropping and re-creating the tables leaves them empty: CREATE IF NOT
        // EXISTS never backfills an external-content FTS5 table.
        indexManager.createIndexes(executor())
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken").isEmpty())
        indexManager.rebuild(executor())
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken").contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chicken\"").contains(id))
    }

    @Test
    fun `ensureIndexes rebuilds a dropped index from existing content`() {
        val id = insertFood("Chicken breast")
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE ${SearchIndexManager.FTS_TABLE}")
            statement.execute("DROP TABLE ${SearchIndexManager.TRIGRAM_TABLE}")
        }
        // Simulate opening a database whose index tables were lost: onOpen
        // repair must both recreate the tables and backfill them from food_items.
        indexManager.ensureIndexes(executor(), querier())
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken").contains(id))
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chicken\"").contains(id))
    }

    @Test
    fun `ensureIndexes repairs an index left stale by missing triggers`() {
        val id = insertFood("Chicken breast")
        connection.createStatement().use { statement ->
            statement.execute("DROP TRIGGER ${SearchIndexManager.FTS_TABLE}_ai")
            statement.execute("DROP TRIGGER ${SearchIndexManager.TRIGRAM_TABLE}_ai")
        }
        insertFood("Turkey thigh")
        // The trigger-less row never reached either index...
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "turkey").isEmpty())
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"turk\"").isEmpty())
        // ...until onOpen repair restores triggers and rebuilds the stale tables.
        indexManager.ensureIndexes(executor(), querier())
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken").contains(id))
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "turkey").isNotEmpty())
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"turk\"").isNotEmpty())
    }

    @Test
    fun `ensureIndexes rebuilds an index with stale rows from a missing delete trigger`() {
        val id = insertFood("Chicken breast")
        connection.createStatement().use { statement ->
            statement.execute("DROP TRIGGER ${SearchIndexManager.FTS_TABLE}_ad")
            statement.execute("DROP TRIGGER ${SearchIndexManager.TRIGRAM_TABLE}_ad")
        }
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM food_items WHERE id = $id")
        }
        // The orphaned rowid stays in the index and its _docsize shadow table,
        // where it would otherwise skew BM25 statistics forever...
        val stale = connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT count(*) FROM ${SearchIndexManager.FTS_TABLE}_docsize WHERE id = $id"
            ).getLong(1)
        }
        assertEquals(1, stale)
        // ...until onOpen repair detects the extra indexed rowid and rebuilds.
        indexManager.ensureIndexes(executor(), querier())
        val afterRepair = connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT count(*) FROM ${SearchIndexManager.FTS_TABLE}_docsize WHERE id = $id"
            ).getLong(1)
        }
        assertEquals(0, afterRepair)
        assertTrue(searchIds(SearchIndexManager.FTS_TABLE, "chicken").isEmpty())
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chicken\"").isEmpty())
    }

    @Test
    fun `ensureIndexes does not rebuild an in-sync index on open`() {
        insertFood("Chicken breast")
        var rebuildCount = 0
        val countingExecutor = SearchIndexManager.SqlExecutor { sql ->
            if (sql.contains("'rebuild'")) rebuildCount++
            connection.createStatement().use { it.execute(sql) }
        }
        // A healthy, trigger-synced index must not be rebuilt on every open.
        indexManager.ensureIndexes(countingExecutor, querier())
        indexManager.ensureIndexes(countingExecutor, querier())
        assertEquals(0, rebuildCount)
    }

    @Test
    fun `fuzzy formatter emits safe quoted trigram groups`() {
        assertEquals(
            "(\"chi\" OR \"hic\" OR \"ick\") AND (\"bre\" OR \"res\" OR \"est\")",
            FuzzyQueryFormatter.format("  Chick'n   BREST!! ")
        )
        assertEquals("(\"caf\" OR \"afe\")", FuzzyQueryFormatter.format("Café"))
        assertEquals(null, FuzzyQueryFormatter.format(""))
        assertEquals(null, FuzzyQueryFormatter.format("a b"))
        assertEquals("(\"abc\")", FuzzyQueryFormatter.format("a abc b"))
        assertEquals(listOf("chi", "hic", "ick", "ckn"), FuzzyQueryFormatter.trigrams("chickn"))
    }

    @Test
    fun `fallback index is used when trigram is unavailable`() {
        // Simulate a SQLite build without the trigram tokenizer: the trigram
        // CREATE fails, createIndexes falls back to a unicode61 table with the
        // same name, and fuzzy queries degrade to exact-token matching.
        val manager = SearchIndexManager()
        manager.createIndexes { sql ->
            if (sql.contains("tokenize = 'trigram remove_diacritics 1'")) {
                throw RuntimeException("no such tokenizer: trigram")
            }
            connection.createStatement().use { it.execute(sql) }
        }
        assertFalse(manager.isTrigramIndexActive)
        val id = insertFood("Chicken breast")
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chicken\"").contains(id))
        assertFalse(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chickn\"").contains(id))
    }

    @Test
    fun `ensureIndexes keeps the degraded unicode61 fallback active`() {
        // Remove the real trigram table so the fallback unicode61 table is
        // genuinely created, then verify the onOpen repair path (ensureIndexes)
        // still reports the fallback as inactive and keeps exact-token matching.
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE ${SearchIndexManager.TRIGRAM_TABLE}")
        }
        val manager = SearchIndexManager()
        manager.ensureIndexes(
            executor = SearchIndexManager.SqlExecutor { sql ->
                if (sql.contains("tokenize = 'trigram remove_diacritics 1'")) {
                    throw RuntimeException("no such tokenizer: trigram")
                }
                connection.createStatement().use { it.execute(sql) }
            },
            querier = querier(),
        )
        assertFalse(manager.isTrigramIndexActive)
        val id = insertFood("Chicken breast")
        assertTrue(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chicken\"").contains(id))
        assertFalse(searchIds(SearchIndexManager.TRIGRAM_TABLE, "\"chickn\"").contains(id))
    }
}