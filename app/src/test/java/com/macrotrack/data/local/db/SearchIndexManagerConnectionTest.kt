package com.macrotrack.data.local.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for the [androidx.sqlite.SQLiteConnection] overloads
 * of [SearchIndexManager.createIndexes] / [SearchIndexManager.ensureIndexes].
 *
 * Room 2.8 invokes `RoomDatabase.Callback.onCreate/onOpen(SQLiteConnection)`
 * when a real SQLite driver (BundledSQLiteDriver) opens the database; if the
 * callback only overrode the legacy `SupportSQLiteDatabase` overloads, the FTS
 * index would never be created and search crashed with "no such table:
 * food_items_fts". These tests pin the connection-API path to the same DDL,
 * repair and fallback behavior as the support-database path.
 */
class SearchIndexManagerConnectionTest {

    /** In-memory fake: records every prepared statement and answers rows. */
    private class FakeConnection(
        private val row: (String) -> String? = { null },
        private val failure: (String) -> RuntimeException? = { null },
    ) : SQLiteConnection {
        val prepared = mutableListOf<String>()

        override fun inTransaction(): Boolean = false

        override fun prepare(sql: String): SQLiteStatement {
            prepared += sql
            return FakeStatement(row(sql), failure(sql))
        }

        override fun close() {}
    }

    private class FakeStatement(
        private val row: String?,
        private val failure: RuntimeException?,
    ) : SQLiteStatement {
        private var rowConsumed = false

        override fun step(): Boolean {
            failure?.let { throw it }
            return if (!rowConsumed && row != null) {
                rowConsumed = true
                true
            } else {
                false
            }
        }

        override fun getText(column: Int): String = row ?: throw IllegalStateException("column $column is NULL")

        override fun isNull(column: Int): Boolean = row == null

        override fun close() {}

        override fun bindBlob(index: Int, value: ByteArray) = throw UnsupportedOperationException()
        override fun bindDouble(index: Int, value: Double) = throw UnsupportedOperationException()
        override fun bindLong(index: Int, value: Long) = throw UnsupportedOperationException()
        override fun bindText(index: Int, value: String) = throw UnsupportedOperationException()
        override fun bindNull(index: Int) = throw UnsupportedOperationException()
        override fun getBlob(index: Int): ByteArray = throw UnsupportedOperationException()
        override fun getDouble(index: Int): Double = throw UnsupportedOperationException()
        override fun getLong(index: Int): Long = throw UnsupportedOperationException()
        override fun getColumnCount(): Int = throw UnsupportedOperationException()
        override fun getColumnName(index: Int): String = throw UnsupportedOperationException()
        override fun getColumnType(index: Int): Int = throw UnsupportedOperationException()
        override fun reset() = throw UnsupportedOperationException()
        override fun clearBindings() = throw UnsupportedOperationException()
    }

    private val trigramDdl = SearchIndexManager.CREATE_TRIGRAM_TABLE

    @Test
    fun `createIndexes runs the full DDL through the connection API`() {
        val connection = FakeConnection()
        val indexManager = SearchIndexManager()

        indexManager.createIndexes(connection)

        assertEquals(
            listOf(
                SearchIndexManager.CREATE_FTS_TABLE,
                *SearchIndexManager.TRIGGERS.take(3).toTypedArray(),
                SearchIndexManager.CREATE_TRIGRAM_TABLE,
                *SearchIndexManager.TRIGGERS.drop(3).toTypedArray(),
            ),
            connection.prepared,
        )
        assertTrue(indexManager.isTrigramIndexActive)
    }

    @Test
    fun `ensureIndexes repairs an out-of-sync index through the connection API`() {
        val connection = FakeConnection(row = { sql ->
            when {
                // Report missing rows for the main index: forces a rebuild.
                sql.startsWith("SELECT count(*)") && sql.contains("food_items_fts_docsize") -> "1"
                // The trigram index is in sync.
                sql.startsWith("SELECT count(*)") -> "0"
                // The trigram tokenizer is active.
                sql.startsWith("SELECT sql FROM sqlite_master") -> trigramDdl
                else -> null
            }
        })
        val indexManager = SearchIndexManager()

        indexManager.ensureIndexes(connection)

        val rebuild = "INSERT INTO ${SearchIndexManager.FTS_TABLE}(${SearchIndexManager.FTS_TABLE}) VALUES('rebuild')"
        assertTrue(
            "expected rebuild of the main index, prepared=${connection.prepared}",
            connection.prepared.contains(rebuild),
        )
        assertTrue(
            "no rebuild expected for the in-sync trigram index",
            connection.prepared.none {
                it.startsWith("INSERT INTO ${SearchIndexManager.TRIGRAM_TABLE}")
            },
        )
        assertTrue(indexManager.isTrigramIndexActive)
    }

    @Test
    fun `missing trigram tokenizer degrades through the connection API`() {
        val connection = FakeConnection(failure = { sql ->
            if (sql.contains("tokenize = 'trigram")) RuntimeException("no such tokenizer: trigram") else null
        })
        val indexManager = SearchIndexManager()

        indexManager.createIndexes(connection)

        assertTrue(connection.prepared.contains(SearchIndexManager.CREATE_TRIGRAM_FALLBACK_TABLE))
        assertFalse(indexManager.isTrigramIndexActive)
    }
}
