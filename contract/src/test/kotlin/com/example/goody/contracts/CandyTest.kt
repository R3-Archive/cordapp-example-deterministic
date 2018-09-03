package com.example.goody.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class CandyTest {
    @Test
    fun testCaseInsensitive() {
        assertEquals("TOFFEE", Candy("Toffee").type)
        assertEquals("TOFFEE", Candy("TOFFEE").type)
        assertEquals("TOFFEE", Candy("toffee").type)
    }

    @Test
    fun testEquality() {
        val nougat1 = Candy("Nougat")
        val nougat2 = Candy("Nougat")
        assertNotSame(nougat1, nougat2)
        assertEquals(nougat1, nougat2)
    }

    @Test
    fun testToString() {
        assertEquals("NOUGAT", Candy("Nougat").toString())
    }
}