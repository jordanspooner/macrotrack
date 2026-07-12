package com.macrotrack.dbbuilder.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServingSizeParserTest {

    private val parser = ServingSizeParser()

    @Test
    fun `parses explicit grams in parentheses`() {
        val result = parser.parse("1 bar (48 g)")
        assertEquals(48f, result!!.grams)
        assertEquals("1 bar (48 g)", result.label)
    }

    @Test
    fun `parses grams without space`() {
        val result = parser.parse("1 slice (25g)")
        assertEquals(25f, result!!.grams)
    }

    @Test
    fun `parses fraction quantities`() {
        val result = parser.parse("1/2 pack (200g)")
        assertEquals(200f, result!!.grams)
    }

    @Test
    fun `parses leading quantity`() {
        val result = parser.parse("250 ml")
        assertEquals(250f, result!!.grams)
    }

    @Test
    fun `defaults to 100g when no quantity`() {
        assertEquals(100f, parser.parseGrams("1 portion"))
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(parser.parse("   "))
    }

    @Test
    fun `treats ml as grams`() {
        val result = parser.parse("1 carton (330 ml)")
        assertEquals(330f, result!!.grams)
    }
}
