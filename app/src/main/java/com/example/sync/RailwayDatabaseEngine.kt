package com.example.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLDecoder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Properties

data class ParsedDbConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val host: String,
    val port: Int,
    val dbName: String
)

/**
 * Handles high-volume (19,000+ records) Railway PostgreSQL database synchronization.
 * Supports direct PostgreSQL connection URLs (e.g. postgresql://user:pass@host:port/dbname).
 * Features:
 * - Robust URL parser supporting URL-encoded passwords, special chars, and Railway dashboard copy-pastes
 * - Persistent single-connection reuse across batches
 * - PostgreSQL JDBC reWriteBatchedInserts for 20x faster batch insertion
 * - Cached working SSL strategy for instant connection reuse
 * - Clean human-friendly error messages and crash-safe rollback
 */
object RailwayDatabaseEngine {
    private const val TAG = "RailwayDatabaseEngine"

    @Volatile
    private var cachedWorkingStrategy: String? = null

    fun parseUrl(rawUrl: String): ParsedDbConfig {
        var clean = rawUrl.trim().removeSurrounding("\"").removeSurrounding("'").trim()

        // Strip common prefixes when users copy full env vars from Railway dashboard or terminal
        val prefixesToStrip = listOf(
            "DATABASE_PUBLIC_URL=",
            "DATABASE_URL=",
            "DATABASE_PRIVATE_URL=",
            "DATABASE_URL:",
            "psql ",
            "PGURI="
        )
        for (prefix in prefixesToStrip) {
            if (clean.startsWith(prefix, ignoreCase = true)) {
                clean = clean.substring(prefix.length).trim().removeSurrounding("\"").removeSurrounding("'").trim()
            }
        }

        val withoutScheme = clean
            .removePrefix("jdbc:")
            .let {
                when {
                    it.startsWith("postgresql://", ignoreCase = true) -> it.substring("postgresql://".length)
                    it.startsWith("postgres://", ignoreCase = true) -> it.substring("postgres://".length)
                    else -> it
                }
            }

        // Split query parameters if any (e.g. ?sslmode=...)
        val queryIndex = withoutScheme.indexOf('?')
        val mainPart = if (queryIndex >= 0) withoutScheme.substring(0, queryIndex) else withoutScheme

        // Find credentials vs destination by splitting at the LAST '@'
        val atIndex = mainPart.lastIndexOf('@')
        val (userInfo, hostAndDb) = if (atIndex >= 0) {
            Pair(mainPart.substring(0, atIndex), mainPart.substring(atIndex + 1))
        } else {
            Pair("", mainPart)
        }

        // Extract user and password (support URL-decoding)
        val user: String
        val pass: String
        if (userInfo.isNotBlank()) {
            val colonIndex = userInfo.indexOf(':')
            if (colonIndex >= 0) {
                user = try { URLDecoder.decode(userInfo.substring(0, colonIndex), "UTF-8") } catch (_: Exception) { userInfo.substring(0, colonIndex) }.ifBlank { "postgres" }
                pass = try { URLDecoder.decode(userInfo.substring(colonIndex + 1), "UTF-8") } catch (_: Exception) { userInfo.substring(colonIndex + 1) }
            } else {
                user = try { URLDecoder.decode(userInfo, "UTF-8") } catch (_: Exception) { userInfo }.ifBlank { "postgres" }
                pass = ""
            }
        } else {
            user = "postgres"
            pass = ""
        }

        // Extract host, port, and dbName from hostAndDb (e.g. "junction.proxy.rlwy.net:12345/railway")
        val slashIndex = hostAndDb.indexOf('/')
        val (hostAndPort, dbNameRaw) = if (slashIndex >= 0) {
            Pair(hostAndDb.substring(0, slashIndex), hostAndDb.substring(slashIndex + 1))
        } else {
            Pair(hostAndDb, "railway")
        }

        val dbName = dbNameRaw.ifBlank { "railway" }
        val colonPortIndex = hostAndPort.lastIndexOf(':')
        val host: String
        val port: Int
        if (colonPortIndex >= 0) {
            host = hostAndPort.substring(0, colonPortIndex).ifBlank { "localhost" }
            port = hostAndPort.substring(colonPortIndex + 1).toIntOrNull() ?: 5432
        } else {
            host = hostAndPort.ifBlank { "localhost" }
            port = 5432
        }

        val jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
        return ParsedDbConfig(
            jdbcUrl = jdbcUrl,
            user = user,
            password = pass,
            host = host,
            port = port,
            dbName = dbName
        )
    }

    suspend fun <T> withConnection(rawUrl: String, block: suspend (Connection) -> T): T = withContext(Dispatchers.IO) {
        val conn = getConnection(rawUrl)
        try {
            block(conn)
        } finally {
            try {
                if (!conn.isClosed) conn.close()
            } catch (_: Throwable) {}
        }
    }

    suspend fun getConnection(rawUrl: String): Connection = withContext(Dispatchers.IO) {
        val originalClassLoader = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = RailwayDatabaseEngine::class.java.classLoader
        } catch (_: Throwable) {}

        val cfg = parseUrl(rawUrl)
        val driver: java.sql.Driver = try {
            val d = org.postgresql.Driver()
            try { DriverManager.registerDriver(d) } catch (_: Exception) {}
            d
        } catch (e: Throwable) {
            Log.w(TAG, "Driver instantiation fallback", e)
            val clazz = Class.forName("org.postgresql.Driver")
            val d = clazz.getDeclaredConstructor().newInstance() as java.sql.Driver
            try { DriverManager.registerDriver(d) } catch (_: Exception) {}
            d
        }

        // Base connection properties
        val baseProps = Properties().apply {
            if (cfg.user.isNotBlank()) setProperty("user", cfg.user)
            if (cfg.password.isNotBlank()) setProperty("password", cfg.password)
            setProperty("connectTimeout", "10")
            setProperty("socketTimeout", "60")
            setProperty("tcpKeepAlive", "true")
            setProperty("reWriteBatchedInserts", "true")
            setProperty("ApplicationName", "BirrFlow-Android")
        }

        var lastException: Throwable? = null

        // If we previously found a working strategy for this session, try it first for instant connection!
        val cached = cachedWorkingStrategy
        if (cached != null) {
            try {
                val conn = tryConnectWithStrategy(driver, cfg, baseProps, cached)
                if (conn != null && !conn.isClosed) return@withContext conn
            } catch (e: Throwable) {
                lastException = e
                cachedWorkingStrategy = null
            }
        }

        val strategies = listOf("require", "nonvalidating", "prefer", "disable", "drivermanager")
        for (strategy in strategies) {
            if (strategy == cached) continue // Already tried
            try {
                val conn = tryConnectWithStrategy(driver, cfg, baseProps, strategy)
                if (conn != null && !conn.isClosed) {
                    cachedWorkingStrategy = strategy
                    return@withContext conn
                }
            } catch (e: Throwable) {
                lastException = e
                Log.w(TAG, "Connection strategy '$strategy' failed: ${e.message}")
            }
        }

        try {
            Thread.currentThread().contextClassLoader = originalClassLoader
        } catch (_: Throwable) {}

        val meaningfulMsg = generateMeaningfulErrorMessage(lastException)
        throw java.sql.SQLException(meaningfulMsg, lastException)
    }

    private fun tryConnectWithStrategy(
        driver: java.sql.Driver,
        cfg: ParsedDbConfig,
        baseProps: Properties,
        strategy: String
    ): Connection? {
        val props = Properties().apply { putAll(baseProps) }
        return when (strategy) {
            "require" -> {
                props.setProperty("ssl", "true")
                props.setProperty("sslmode", "require")
                driver.connect("${cfg.jdbcUrl}?ssl=true&sslmode=require&connectTimeout=10", props)
            }
            "nonvalidating" -> {
                props.setProperty("ssl", "true")
                props.setProperty("sslmode", "require")
                props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory")
                driver.connect("${cfg.jdbcUrl}?ssl=true&sslmode=require&sslfactory=org.postgresql.ssl.NonValidatingFactory&connectTimeout=10", props)
            }
            "prefer" -> {
                props.setProperty("sslmode", "prefer")
                driver.connect("${cfg.jdbcUrl}?sslmode=prefer&connectTimeout=10", props)
            }
            "disable" -> {
                props.setProperty("sslmode", "disable")
                driver.connect("${cfg.jdbcUrl}?sslmode=disable&connectTimeout=10", props)
            }
            "drivermanager" -> {
                props.setProperty("ssl", "true")
                props.setProperty("sslmode", "require")
                DriverManager.getConnection("${cfg.jdbcUrl}?ssl=true&sslmode=require&connectTimeout=10", props)
            }
            else -> null
        }
    }

    private fun generateMeaningfulErrorMessage(e: Throwable?): String {
        if (e == null) return "Could not connect to Railway PostgreSQL database."
        var curr: Throwable? = e
        while (curr != null) {
            val msg = curr.message
            if (!msg.isNullOrBlank()) {
                val lower = msg.lowercase()
                when {
                    lower.contains("password authentication failed") ->
                        return "Invalid password or username in Railway database URL."
                    lower.contains("unknownhost") || lower.contains("unresolved") || lower.contains("name or service not known") ->
                        return "Could not reach database host. Check the Railway proxy URL and internet connection."
                    lower.contains("connection refused") ->
                        return "Connection refused. Make sure your Railway database is active and public networking is enabled."
                    lower.contains("timed out") || lower.contains("timeout") ->
                        return "Database connection timed out. Check network connection."
                    !msg.contains("something unusual has occurred", ignoreCase = true) ->
                        return msg
                }
            }
            curr = curr.cause
        }
        return e.localizedMessage ?: "Could not connect to Railway PostgreSQL database."
    }

    suspend fun initSchema(rawUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getConnection(rawUrl).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS devices (
                            device_id VARCHAR(128) PRIMARY KEY,
                            friendly_name VARCHAR(256),
                            model_name VARCHAR(256),
                            updated_at BIGINT
                        );
                    """.trimIndent())

                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS sync_items (
                            doc_id VARCHAR(256) PRIMARY KEY,
                            device_id VARCHAR(128),
                            payload BYTEA,
                            ts BIGINT
                        );
                    """.trimIndent())

                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_items_dev ON sync_items(device_id);")
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_items_dev_ts ON sync_items(device_id, ts);")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Railway database schema", e)
            throw e
        }
    }

    suspend fun publishDevice(rawUrl: String, deviceId: String, friendlyName: String, modelName: String): Unit = withContext(Dispatchers.IO) {
        val deleteOldSql = "DELETE FROM devices WHERE (friendly_name = ? OR (model_name = ? AND model_name != 'Android Phone')) AND device_id != ?"
        val upsertSql = """
            INSERT INTO devices (device_id, friendly_name, model_name, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (device_id) DO UPDATE SET
                friendly_name = EXCLUDED.friendly_name,
                model_name = EXCLUDED.model_name,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()

        getConnection(rawUrl).use { conn ->
            try {
                conn.prepareStatement(deleteOldSql).use { stmt ->
                    stmt.setString(1, friendlyName)
                    stmt.setString(2, modelName)
                    stmt.setString(3, deviceId)
                    stmt.executeUpdate()
                }
            } catch (_: Exception) {}

            conn.prepareStatement(upsertSql).use { stmt ->
                stmt.setString(1, deviceId)
                stmt.setString(2, friendlyName)
                stmt.setString(3, modelName)
                stmt.setLong(4, System.currentTimeMillis())
                stmt.executeUpdate()
            }
        }
    }

    suspend fun fetchDiscoveredDevices(rawUrl: String, localDeviceId: String, syncedIds: Set<String>): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val sql = "SELECT device_id, friendly_name, model_name, updated_at FROM devices ORDER BY updated_at DESC"
        val list = ArrayList<DiscoveredDevice>()
        val seenIds = HashSet<String>()
        val seenNames = HashSet<String>()
        getConnection(rawUrl).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        val id = rs.getString("device_id") ?: continue
                        val name = rs.getString("friendly_name") ?: id
                        val model = rs.getString("model_name") ?: "Android Phone"
                        val lastAct = rs.getLong("updated_at")
                        if (!seenIds.add(id)) continue
                        if (!seenNames.add(name) && id != localDeviceId) continue
                        list += DiscoveredDevice(
                            deviceId = id,
                            friendlyName = name,
                            modelName = model,
                            isCurrentDevice = (id == localDeviceId),
                            isSynced = (id == localDeviceId || id in syncedIds),
                            lastActive = lastAct
                        )
                    }
                }
            }
        }
        list
    }

    suspend fun uploadBatchWithConnection(
        conn: Connection,
        deviceId: String,
        records: List<SealedRecord>
    ): Unit = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        val sql = """
            INSERT INTO sync_items (doc_id, device_id, payload, ts)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (doc_id) DO UPDATE SET
                payload = EXCLUDED.payload,
                ts = EXCLUDED.ts
        """.trimIndent()

        val wasAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            conn.prepareStatement(sql).use { stmt ->
                val now = System.currentTimeMillis()
                for (rec in records) {
                    stmt.setString(1, rec.docId)
                    stmt.setString(2, deviceId)
                    stmt.setBytes(3, rec.payload)
                    stmt.setLong(4, now)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            try { conn.rollback() } catch (_: Throwable) {}
            throw e
        } finally {
            try { conn.autoCommit = wasAutoCommit } catch (_: Throwable) {}
        }
    }

    suspend fun uploadBatch(
        rawUrl: String,
        deviceId: String,
        records: List<SealedRecord>
    ): Unit = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        getConnection(rawUrl).use { conn ->
            uploadBatchWithConnection(conn, deviceId, records)
        }
    }

    suspend fun clearDeviceItems(rawUrl: String, deviceId: String): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM sync_items WHERE device_id = ?"
        getConnection(rawUrl).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, deviceId)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun countRemoteDeviceItems(rawUrl: String, remoteDeviceId: String, afterTs: Long = 0L): Int = withContext(Dispatchers.IO) {
        val sql = if (afterTs > 0L) {
            "SELECT COUNT(*) FROM sync_items WHERE device_id = ? AND ts > ?"
        } else {
            "SELECT COUNT(*) FROM sync_items WHERE device_id = ?"
        }
        getConnection(rawUrl).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, remoteDeviceId)
                if (afterTs > 0L) stmt.setLong(2, afterTs)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }
    }

    suspend fun fetchRemoteDeviceItemsWithConnection(
        conn: Connection,
        remoteDeviceId: String,
        limit: Int,
        offset: Int,
        afterTs: Long = 0L
    ): List<SealedRecord> = withContext(Dispatchers.IO) {
        val sql = if (afterTs > 0L) {
            "SELECT doc_id, device_id, payload FROM sync_items WHERE device_id = ? AND ts > ? ORDER BY ts ASC LIMIT ? OFFSET ?"
        } else {
            "SELECT doc_id, device_id, payload FROM sync_items WHERE device_id = ? ORDER BY ts ASC LIMIT ? OFFSET ?"
        }
        val list = ArrayList<SealedRecord>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, remoteDeviceId)
            if (afterTs > 0L) {
                stmt.setLong(2, afterTs)
                stmt.setInt(3, limit)
                stmt.setInt(4, offset)
            } else {
                stmt.setInt(2, limit)
                stmt.setInt(3, offset)
            }
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val docId = rs.getString("doc_id") ?: continue
                    val devId = rs.getString("device_id") ?: remoteDeviceId
                    val bytes = rs.getBytes("payload") ?: continue
                    list += SealedRecord(docId, devId, bytes)
                }
            }
        }
        list
    }

    suspend fun fetchRemoteDeviceItems(
        rawUrl: String,
        remoteDeviceId: String,
        limit: Int,
        offset: Int,
        afterTs: Long = 0L
    ): List<SealedRecord> = withContext(Dispatchers.IO) {
        getConnection(rawUrl).use { conn ->
            fetchRemoteDeviceItemsWithConnection(conn, remoteDeviceId, limit, offset, afterTs)
        }
    }
}
