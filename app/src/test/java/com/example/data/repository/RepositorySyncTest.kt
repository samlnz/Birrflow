package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.dao.AccountKey
import com.example.data.entity.FlowDirection
import com.example.data.entity.IncomeSourceEntity
import com.example.data.entity.ManualSourceEntity
import com.example.sync.SyncCodec
import com.example.sync.SyncRecords
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two phones with real (in-memory) Room databases exchanging encrypted records exactly like the
 * Firestore push/listener does, minus the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepositorySyncTest {

    private val dbs = mutableListOf<AppDatabase>()
    private val t0 = 1_758_800_000_000L

    @After
    fun close() = dbs.forEach { it.close() }

    private fun newPhone(): Pair<FinancialRepository, AppDatabase> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dbs += db
        val repo = FinancialRepository(db)
        runBlocking { repo.seedInitialDataIfEmpty(); repo.ensureLocalDeviceIdentity() }
        return repo to db
    }

    private fun tele(i: Int, body: String) = IncomingSms("127", body, t0 + i * 60_000L)

    private suspend fun scan(repo: FinancialRepository, msgs: List<IncomingSms>) {
        val phone = repo.localDeviceId()
        val ctx = repo.loadParseContext()
        val dedupe = repo.loadDedupeIndex(phone)
        val summary = repo.ingestBatch(phone, repo.prepare(phone, msgs, ctx), ctx, dedupe)
        repo.reconcileAccounts(summary.affectedAccounts)
    }

    private suspend fun deliver(from: FinancialRepository, to: FinancialRepository, key: ByteArray) {
        val items = from.peekOutbox(1000)
        val records = SyncRecords.seal(items, from, key, from.localDeviceId())
        from.ackOutbox(items)
        assertTrue(SyncRecords.open(records, key, from.localDeviceId()).isEmpty)
        to.applyRemoteChanges(SyncRecords.open(records, key, to.localDeviceId()))
    }

    @Test
    fun twoPhonesStayInSync() = runBlocking {
        val (a, dbA) = newPhone()
        val (b, dbB) = newPhone()
        val daoA = dbA.financialDao()
        val daoB = dbB.financialDao()
        assertNotEquals(a.localDeviceId(), b.localDeviceId())
        val key = SyncCodec.groupKeyFor(SyncCodec.newLinkCode())
        a.setCloudSyncEnabled(true)
        b.setCloudSyncEnabled(true)

        scan(a, listOf(
            tele(0, "You have received ETB 1,000.00 from Abebe Kebede (2519****1111) on 01/09/2026 09:00:00. Your transaction number is DIA0000001. Your current E-Money Account balance is ETB 1,000.00."),
            tele(1, "You have transferred ETB 200.00 to Sara Worku (2519****2222) on 01/09/2026 10:00:00. Your transaction number is DIA0000002. Your current E-Money Account balance is ETB 800.00."),
            tele(2, "You have paid ETB 100.00 to Kaldi's Coffee on 01/09/2026 12:00:00. Your transaction number is DIA0000003. Your current E-Money Account balance is ETB 1,200.00.")
        ))
        val aTx = daoA.getAllTransactionsOnce().filter { !it.isReconciliation }
        assertEquals(3, aTx.size)
        val recon = daoA.getAllTransactionsOnce().single { it.isReconciliation }
        assertEquals(500.0, recon.amount, 0.001)
        assertEquals(FlowDirection.CREDIT, recon.direction)

        deliver(a, b, key)
        assertEquals(daoA.getAllTransactionsOnce().map { it.transactionId }.toSet(), daoB.getAllTransactionsOnce().map { it.transactionId }.toSet())
        assertFalse(daoB.getPhoneById(a.localDeviceId())!!.isCurrentDevice)
        assertTrue(daoB.peekOutbox(1000).none { it.kind == FinancialRepository.KIND_TX })

        val someTx = aTx.first()
        b.updateTransactionCategory(someTx.transactionId, "cat_food", "lunch")
        b.upsertManualSource(ManualSourceEntity("MAN-1", "Cash at home", balance = 2500.0, phoneId = b.localDeviceId()))
        deliver(b, a, key)
        assertEquals("cat_food", daoA.getTransactionById(someTx.transactionId)!!.categoryId)
        assertEquals(2500.0, daoA.getManualSourceById("MAN-1")!!.balance, 0.0)

        a.deleteManualSource("MAN-1")
        deliver(a, b, key)
        assertTrue(daoB.getManualSourceById("MAN-1")!!.isDeleted)
    }

    @Test
    fun upgradeRebuildKeepsIdsAndUserCategories() = runBlocking {
        val (a, db) = newPhone()
        val dao = db.financialDao()
        scan(a, listOf(tele(0, "You have received ETB 50.00 from Ibrahim Ahmed(2519****2326) on 25/09/2026 20:16:00. Your transaction number is DIP900AA02. Your current E-Money Account balance is ETB 9,120.72.")))
        val tx = dao.getAllTransactionsOnce().single()
        a.updateTransactionCategory(tx.transactionId, "cat_business", null)
        a.putState(FinancialRepository.KEY_PARSER_RULES_VERSION, "0")
        assertTrue(a.rebuildFromStoredSmsIfParserChanged())
        val after = dao.getAllTransactionsOnce().single()
        assertEquals(tx.transactionId, after.transactionId)
        assertEquals("cat_business", after.categoryId)
    }

    @Test
    fun creditsOfAssignedPhoneAreFiledUnderItsIncomeSource() = runBlocking {
        val (a, dbA) = newPhone()
        val (b, dbB) = newPhone()
        val daoB = dbB.financialDao()
        val key = SyncCodec.groupKeyFor(SyncCodec.newLinkCode())
        a.setCloudSyncEnabled(true)
        b.setCloudSyncEnabled(true)
        deliver(a, b, key) // B now knows phone A

        b.insertIncomeSource(IncomeSourceEntity("inc_shop", "Retail Shop"))
        b.assignPhoneToIncomeSource(a.localDeviceId(), "inc_shop")

        scan(a, listOf(
            tele(0, "You have received ETB 1,000.00 from Abebe Kebede (2519****1111) on 01/09/2026 09:00:00. Your transaction number is DIA0000001. Your current E-Money Account balance is ETB 1,000.00."),
            tele(1, "You have transferred ETB 200.00 to Sara Worku (2519****2222) on 01/09/2026 10:00:00. Your transaction number is DIA0000002. Your current E-Money Account balance is ETB 800.00."),
            tele(2, "You have paid ETB 100.00 to Kaldi's Coffee on 01/09/2026 12:00:00. Your transaction number is DIA0000003. Your current E-Money Account balance is ETB 1,200.00.")
        ))
        // Phone A re-uploads its own profile, which never carries B's assignment
        a.updatePhone(dbA.financialDao().getPhoneById(a.localDeviceId())!!)
        deliver(a, b, key)

        assertEquals("inc_shop", daoB.getPhoneById(a.localDeviceId())!!.assignedIncomeSourceId)
        val credits = daoB.getAllTransactionsOnce().filter { it.phoneId == a.localDeviceId() && it.direction == FlowDirection.CREDIT }
        assertEquals(2, credits.size) // the received 1,000 and the 500 recovered from the balance gap
        assertTrue(credits.all { it.categoryId == "inc_shop" })
        val debits = daoB.getAllTransactionsOnce().filter { it.phoneId == a.localDeviceId() && it.direction == FlowDirection.DEBIT }
        assertTrue(debits.none { it.categoryId == "inc_shop" })
    }

    @Test
    fun largeFirstScanIsConsistent() = runBlocking {
        val (a, db) = newPhone()
        var bal = 100_000.0
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US)
        val msgs = (0 until 3000).map { i ->
            val dateStr = sdf.format(java.util.Date(t0 + i * 60_000L))
            if (i % 2 == 0) {
                bal += 50
                tele(i, "You have received ETB 50.00 from Person $i (2519****1234) on $dateStr. Your transaction number is DIR${100000 + i}. Your current E-Money Account balance is ETB ${"%.2f".format(bal)}.")
            } else {
                bal -= 20
                tele(i, "You have paid ETB 20.00 to Shop $i on $dateStr. Your transaction number is DIP${100000 + i}. Your current E-Money Account balance is ETB ${"%.2f".format(bal)}.")
            }
        }.reversed()
        val phone = a.localDeviceId()
        val ctx = a.loadParseContext()
        val dedupe = a.loadDedupeIndex(phone)
        val affected = HashSet<AccountKey>()
        for (chunk in listOf(msgs.take(200)) + msgs.drop(200).chunked(1500)) {
            affected += a.ingestBatch(phone, a.prepare(phone, chunk, ctx), ctx, dedupe).affectedAccounts
        }
        a.reconcileAccounts(affected)
        val all = db.financialDao().getAllTransactionsOnce()
        assertEquals(3000, all.count { !it.isReconciliation })
        assertEquals(0, all.count { it.isReconciliation })
    }
}
