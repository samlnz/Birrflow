package com.example.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RailwayConnectionTest {

    @Test
    fun testRailwayUrlParsing() {
        val url = "postgresql://postgres:examplePassw0rd@example.proxy.rlwy.net:12345/railway"
        val cfg = RailwayDatabaseEngine.parseUrl(url)
        assertEquals("example.proxy.rlwy.net", cfg.host)
        assertEquals(42058, cfg.port)
        assertEquals("postgres", cfg.user)
        assertEquals("examplePassw0rd", cfg.password)
        assertEquals("railway", cfg.dbName)
        assertTrue(cfg.jdbcUrl.startsWith("jdbc:postgresql://example.proxy.rlwy.net:12345/railway"))
    }

    @Test
    fun testRailwayUrlWithQuotesParsing() {
        val quotedUrl = "\"postgresql://postgres:examplePassw0rd@example.proxy.rlwy.net:12345/railway\""
        val cfg = RailwayDatabaseEngine.parseUrl(quotedUrl)
        assertEquals("example.proxy.rlwy.net", cfg.host)
        assertEquals(42058, cfg.port)
        assertEquals("postgres", cfg.user)
        assertEquals("examplePassw0rd", cfg.password)
        assertEquals("railway", cfg.dbName)
    }
}

