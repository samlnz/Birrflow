package com.example

import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.InternalTransferConfigEntity
import com.example.data.entity.MatchMethod
import com.example.data.entity.SenderIdentifierType
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.SourceType
import com.example.data.entity.TransactionType
import com.example.data.entity.VerificationStatus
import com.example.parser.SmsParserEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val defaultSources = listOf(
        FinancialSourceEntity("telebirr", "Telebirr", SourceType.MOBILE_MONEY, "Ethio Telecom", true, "telebirr,127,Telebirr"),
        FinancialSourceEntity("cbe", "Commercial Bank of Ethiopia", SourceType.BANK, "CBE", true, "cbe,Commercial Bank of Ethiopia,889"),
        FinancialSourceEntity("boa", "Bank of Abyssinia", SourceType.BANK, "BOA", true, "boa,Bank of Abyssinia"),
        FinancialSourceEntity("cbe_birr", "CBE Birr", SourceType.WALLET, "CBE Birr", true, "cbebirr,cbe birr,CBE Birr")
    )

    private val defaultSenders = listOf(
        SmsSenderIdentifierEntity("S1", "telebirr", "telebirr", null, SenderIdentifierType.NAME, MatchMethod.CASE_INSENSITIVE, true, 1),
        SmsSenderIdentifierEntity("S2", "telebirr", null, "127", SenderIdentifierType.PHONE_NUMBER, MatchMethod.EXACT, true, 2),
        SmsSenderIdentifierEntity("S3", "cbe", "CBE", null, SenderIdentifierType.NAME, MatchMethod.CASE_INSENSITIVE, true, 1),
        SmsSenderIdentifierEntity("S4", "cbe", null, "889", SenderIdentifierType.PHONE_NUMBER, MatchMethod.EXACT, true, 2),
        SmsSenderIdentifierEntity("S5", "boa", "BOA", null, SenderIdentifierType.NAME, MatchMethod.CASE_INSENSITIVE, true, 1),
        SmsSenderIdentifierEntity("S6", "cbe_birr", "CBE Birr", null, SenderIdentifierType.NAME, MatchMethod.CASE_INSENSITIVE, true, 1),
        SmsSenderIdentifierEntity("S7", "cbe_birr", "CBEBirr", null, SenderIdentifierType.NAME, MatchMethod.CASE_INSENSITIVE, true, 2)
    )

    private val internalConfigs = listOf(
        InternalTransferConfigEntity(
            configId = "INT-1",
            identityName = "Self Telebirr",
            identifierValue = "0911223344",
            financialInstitution = "Telebirr",
            isActive = true
        )
    )

    // ==========================================
    // 1. TELEBIRR REAL-WORLD FORMAT TESTS
    // ==========================================
    @Test
    fun testTelebirrTransferredDebit() {
        val sms = """
Dear Hana 
You have transferred ETB 1,000.00 to kaleb haile (2519****1879) on 25/09/2026 20:08:48. Your transaction number is DIP900AA01. The service fee is  ETB 3.48 and  15% VAT on the service fee is ETB 0.52. Your current E-Money Account  balance is ETB 9,011.72. To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DIP900AA01.

Thank you for using telebirr
Ethio telecom
        """.trimIndent()

        val result = SmsParserEngine.parseSms("telebirr", sms, defaultSources, emptyList(), defaultSenders, internalConfigs)

        assertFalse(result.isUnknownSender)
        assertEquals("telebirr", result.detectedSourceId)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals(1000.00, result.amount ?: 0.0, 0.01)
        assertEquals("kaleb haile (2519****1879)", result.recipient)
        assertEquals("DIP900AA01", result.referenceNumber)
        assertEquals(4.00, result.fee, 0.01)
        assertEquals(9011.72, result.balanceAfter ?: 0.0, 0.01)
        assertEquals(VerificationStatus.VALID, result.verificationStatus)
    }

    @Test
    fun testTelebirrReceivedCredit() {
        val sms = """
Dear Hana 
You have received ETB 50.00 from Ibrahim Ahmed(2519****2326)  on 25/09/2026 20:16:00. Your transaction number is DIP900AA02. Your current E-Money Account balance is ETB 9,120.72.
Thank you for using telebirr
Ethio telecom
        """.trimIndent()

        val result = SmsParserEngine.parseSms("telebirr", sms, defaultSources, emptyList(), defaultSenders, internalConfigs)

        assertFalse(result.isUnknownSender)
        assertEquals("telebirr", result.detectedSourceId)
        assertEquals(TransactionType.INCOME, result.transactionType)
        assertEquals(50.00, result.amount ?: 0.0, 0.01)
        assertEquals("Ibrahim Ahmed(2519****2326)", result.sender)
        assertEquals("DIP900AA02", result.referenceNumber)
        assertEquals(9120.72, result.balanceAfter ?: 0.0, 0.01)
        assertEquals(VerificationStatus.VALID, result.verificationStatus)
    }

    // ==========================================
    // 2. CBE BIRR REAL-WORLD FORMAT TESTS
    // ==========================================
    @Test
    fun testCbeBirrSentDebit() {
        val sms = "Dear NAHOM, you have sent 2,500.00Br. to dagim tadele on 25/09/26 10:30,Txn ID DIP900AA003. Your CBE Birr account balance is 37.32Br.Thank you! For invoice https://cbepay1.cbe.com.et/aureceipt?TID=DIP900AA003&PH=251900000869 For your feedback please click the link https://shorturl.at/gy3A0"

        val result = SmsParserEngine.parseSms("CBE Birr", sms, defaultSources, emptyList(), defaultSenders, internalConfigs)

        assertFalse(result.isUnknownSender)
        assertEquals("cbe_birr", result.detectedSourceId)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals(2500.00, result.amount ?: 0.0, 0.01)
        assertEquals("dagim tadele", result.recipient)
        assertEquals("DIP900AA003", result.referenceNumber)
        assertEquals(37.32, result.balanceAfter ?: 0.0, 0.01)
        assertEquals(VerificationStatus.VALID, result.verificationStatus)
    }

    @Test
    fun testCbeBirrReceivedCredit() {
        val sms = "Dear NAHOM, you received 50.00Br. from 251900000526 - Liya Abera Tola on 23/09/26 17:34,Txn ID DIN900AA004.Your CBE Birr account balance is 2,407.32Br. Thank you! For invoice https://cbepay1.cbe.com.et/aureceipt?TID=DIN900AA004&PH=251900000869"

        val result = SmsParserEngine.parseSms("CBE Birr", sms, defaultSources, emptyList(), defaultSenders, internalConfigs)

        assertFalse(result.isUnknownSender)
        assertEquals("cbe_birr", result.detectedSourceId)
        assertEquals(TransactionType.INCOME, result.transactionType)
        assertEquals(50.00, result.amount ?: 0.0, 0.01)
        assertEquals("251900000526 - Liya Abera Tola", result.sender)
        assertEquals("DIN900AA004", result.referenceNumber)
        assertEquals(2407.32, result.balanceAfter ?: 0.0, 0.01)
        assertEquals(VerificationStatus.VALID, result.verificationStatus)
    }

    // ==========================================
    // 3. BANK OF ABYSSINIA (BOA) REAL-WORLD FORMAT TESTS
    // ==========================================
    @Test
    fun testBoaDebited() {
        val sms = """
Dear Nahom, your account 1*83 was debited with ETB 30,018.00. Available Balance: ETB 402,381.24.
Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT2623208AAA11083
Feedback: https://cs.bankofabyssinia.com/cs/?trx=DFT2623208XKC
Link your Fayda: https://cs.bankofabyssinia.com/fayda_connect 
For help, call 8397 (24/7 Toll-Free). Bank of Abyssinia.
        """.trimIndent()

        val result = SmsParserEngine.parseSms("BOA", sms, defaultSources, emptyList(), defaultSenders, internalConfigs)

        assertFalse(result.isUnknownSender)
        assertEquals("boa", result.detectedSourceId)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals(30018.00, result.amount ?: 0.0, 0.01)
        assertEquals(402381.24, result.balanceAfter ?: 0.0, 0.01)
        assertEquals("FT2623208AAA11083", result.referenceNumber)
        assertEquals("BOA 1*83", result.accountIdentifier)
        assertEquals(VerificationStatus.VALID, result.verificationStatus)
    }

    @Test
    fun testBoaCredited() {
        val sms = """
Dear Nahom, your account 1*83 was credited with ETB 20,000.00 by  Dagimtadeleworku . Available Balance: ETB 823,000.80.
Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT26268782AA10104
Feedback: https://cs.bankofabyssinia.com/cs/?trx=CFT26268782NR
Link your Fayda: https://cs.bankofabyssinia.com/fayda_connect 
For help, call 8397 (24/7 Toll-Free). Bank of Abyssinia.
        """.trimIndent()

        val result = SmsParserEngine.parseSms("BOA", sms, defaultSources, emptyList(), defaultSenders, internalConfigs)

        assertFalse(result.isUnknownSender)
        assertEquals("boa", result.detectedSourceId)
        assertEquals(TransactionType.INCOME, result.transactionType)
        assertEquals(20000.00, result.amount ?: 0.0, 0.01)
        assertEquals("Dagimtadeleworku", result.sender)
        assertEquals(823000.80, result.balanceAfter ?: 0.0, 0.01)
        assertEquals("FT26268782AA10104", result.referenceNumber)
        assertEquals(VerificationStatus.VALID, result.verificationStatus)
    }

    // ==========================================
    // 4. COMMERCIAL BANK OF ETHIOPIA (CBE) REAL-WORLD FORMAT TESTS
    // ==========================================
    @Test
    fun testCbeDebitedWithVatTotal() {
        val sms = "Dear Nahom your Account 1*****0032 has been debited with ETB23,626.00 .Including Service charge and VAT(15%) with a total of ETB 23754.05. Your Current Balance is ETB 10,047.96. Thank you for Banking with CBE! https://apps.cbe.com.et:100/?id=FT26268AAAAA48220032"

        val result = SmsParserEngine.parseSms("CBE", sms, defaultSources, emptyList(), defaultSenders, internalConfigs)

        assertFalse(result.isUnknownSender)
        assertEquals("cbe", result.detectedSourceId)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals(23626.00, result.amount ?: 0.0, 0.01)
        assertEquals(128.05, result.fee, 0.01)
        assertEquals(10047.96, result.balanceAfter ?: 0.0, 0.01)
        assertEquals("FT26268AAAAA48220032", result.referenceNumber)
        assertEquals(VerificationStatus.VALID, result.verificationStatus)
    }

    @Test
    fun testCbeReceivedCredit() {
        val sms = "Dear Nahom Tadele Worku You have received ETB 4,000.00 from account 1**2152 (Yonas Tadesse Girma) to your account 1**0032. Your current balance is ETB34,002.01. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-xxxxxxxxxxxxxxxxxxxx  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"

        val result = SmsParserEngine.parseSms("CBE", sms, defaultSources, emptyList(), defaultSenders, internalConfigs)

        assertFalse(result.isUnknownSender)
        assertEquals("cbe", result.detectedSourceId)
        assertEquals(TransactionType.INCOME, result.transactionType)
        assertEquals(4000.00, result.amount ?: 0.0, 0.01)
        assertEquals("Yonas Tadesse Girma", result.sender)
        assertEquals(34002.01, result.balanceAfter ?: 0.0, 0.01)
        assertEquals("v2-xxxxxxxxxxxxxxxxxxxx", result.referenceNumber)
        assertEquals(VerificationStatus.VALID, result.verificationStatus)
    }

    // ==========================================
    // 5. SMART INTERNAL TRANSFER BY RECEIVER/SENDER NAME ONLY
    // ==========================================
    @Test
    fun testInternalTransferIdentifiedByReceiverNameVariations() {
        val boaSms = """
Dear Nahom, your account 1*83 was credited with ETB 20,000.00 by  Dagimtadeleworku . Available Balance: ETB 823,000.80.
Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT26268782AA10104
Feedback: https://cs.bankofabyssinia.com/cs/?trx=CFT26268782NR
Link your Fayda: https://cs.bankofabyssinia.com/fayda_connect 
For help, call 8397 (24/7 Toll-Free). Bank of Abyssinia.
        """.trimIndent()

        // Case A: User sets identity name as "dagim"
        val configsA = listOf(InternalTransferConfigEntity("INT-1", "dagim", "", "BOA", isActive = true))
        val resultA = SmsParserEngine.parseSms("BOA", boaSms, defaultSources, emptyList(), defaultSenders, configsA)
        assertEquals(TransactionType.INTERNAL_TRANSFER, resultA.transactionType)
        assertEquals(20000.00, resultA.amount ?: 0.0, 0.01)
        assertEquals(823000.80, resultA.balanceAfter ?: 0.0, 0.01)
        assertEquals("BOA 1*83", resultA.accountIdentifier)

        // Case B: User sets identity name as "dagim tadele worku"
        val configsB = listOf(InternalTransferConfigEntity("INT-2", "dagim tadele worku", "", "BOA", isActive = true))
        val resultB = SmsParserEngine.parseSms("BOA", boaSms, defaultSources, emptyList(), defaultSenders, configsB)
        assertEquals(TransactionType.INTERNAL_TRANSFER, resultB.transactionType)

        // Case C: User sets identity name as "Dagimtadeleworku"
        val configsC = listOf(InternalTransferConfigEntity("INT-3", "Dagimtadeleworku", "", "BOA", isActive = true))
        val resultC = SmsParserEngine.parseSms("BOA", boaSms, defaultSources, emptyList(), defaultSenders, configsC)
        assertEquals(TransactionType.INTERNAL_TRANSFER, resultC.transactionType)

        // Case D: User sets identity in all uppercase "DAGIM"
        val configsD = listOf(InternalTransferConfigEntity("INT-4", "DAGIM", "", "BOA", isActive = true))
        val resultD = SmsParserEngine.parseSms("BOA", boaSms, defaultSources, emptyList(), defaultSenders, configsD)
        assertEquals(TransactionType.INTERNAL_TRANSFER, resultD.transactionType)
    }

    // ==========================================
    // 7. YESTERDAY & HISTORICAL SMS TIMESTAMP EXTRACTION
    // ==========================================
    @Test
    fun testYesterdayHistoricalSmsDateExtraction() {
        // Suppose yesterday was 24/09/2026 15:30:00 (timestamp: 1790349000000)
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US)
        val yesterdayDate = sdf.parse("24/09/2026 15:30:00")!!.time

        val boaSms = "Dear Nahom, your account 1*83 was debited with ETB 5,000.00. Available Balance: ETB 100,000.00. Bank of Abyssinia."
        val result = SmsParserEngine.parseSms("BOA", boaSms, defaultSources, emptyList(), defaultSenders, internalConfigs, receivedTimestamp = yesterdayDate)

        assertEquals("24/09/2026", result.transactionDate)
        assertEquals("15:30", result.transactionTime)

        // Verify date matching in MainViewModel.isSameDay
        val isMatchYesterday = com.example.ui.viewmodel.MainViewModel.isSameDay(result.transactionDate ?: "", yesterdayDate, "24/09/2026")
        assertTrue(isMatchYesterday)

        val isMatchToday = com.example.ui.viewmodel.MainViewModel.isSameDay(result.transactionDate ?: "", yesterdayDate, "25/09/2026")
        assertFalse(isMatchToday)
    }
}

