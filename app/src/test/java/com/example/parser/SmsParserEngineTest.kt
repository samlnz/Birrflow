package com.example.parser

import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.InternalTransferConfigEntity
import com.example.data.entity.MatchMethod
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.SourceType
import com.example.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-world Telebirr / CBE / BOA wordings that the previous parser silently dropped. */
class SmsParserEngineTest {
    private val sources = listOf(
        FinancialSourceEntity("telebirr", "Telebirr", SourceType.MOBILE_MONEY, "Ethio Telecom", true, "telebirr,127,Telebirr"),
        FinancialSourceEntity("cbe", "Commercial Bank of Ethiopia", SourceType.BANK, "CBE", true, "cbe,Commercial Bank of Ethiopia,889"),
        FinancialSourceEntity("boa", "Bank of Abyssinia", SourceType.BANK, "BOA", true, "boa,Bank of Abyssinia,BankofAbyssinia"),
        FinancialSourceEntity("cbe_birr", "CBE Birr", SourceType.WALLET, "CBE Birr", true, "cbebirr,cbe birr,CBE Birr")
    )
    private val identifiers = listOf(
        SmsSenderIdentifierEntity("SND-001", "telebirr", senderName = "telebirr"),
        SmsSenderIdentifierEntity("SND-002", "telebirr", senderNumber = "127", matchMethod = MatchMethod.EXACT),
        SmsSenderIdentifierEntity("SND-004", "cbe", senderName = "CBE"),
        SmsSenderIdentifierEntity("SND-005", "cbe", senderName = "Commercial Bank", matchMethod = MatchMethod.CONTAINS),
        SmsSenderIdentifierEntity("SND-006", "cbe", senderNumber = "889", matchMethod = MatchMethod.CONTAINS),
        SmsSenderIdentifierEntity("SND-007", "boa", senderName = "BOA")
    )
    private val internal = listOf(InternalTransferConfigEntity("INT-1", "Nahom Tadele", "Nahom Tadele", "All Banks"))

    private fun parse(sender: String, body: String, configs: List<InternalTransferConfigEntity> = emptyList()) =
        SmsParserEngine.parseSms(sender, body, sources, emptyList(), identifiers, configs, 1_758_800_000_000)

    @Test fun telebirrTransferWithFeesAndOddSpacing() {
        val r = parse("127", "Dear Nahom \nYou have transferred ETB 1,000.00 to kaleb haile (2519****1879) on 25/09/2026 20:08:48. Your transaction number is DIP900AA01. The service fee is  ETB 3.48 and  15% VAT on the service fee is ETB 0.52. Your current E-Money Account  balance is ETB 9,011.72. To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DIP900AA01\n\nThank you for using telebirr\nEthio telecom")
        assertEquals(1000.0, r.amount); assertEquals(4.0, r.fee, 0.001)
        assertEquals(FlowDirection.DEBIT, r.direction); assertEquals(9011.72, r.balanceAfter)
        assertEquals("DIP900AA01", r.referenceNumber); assertEquals("telebirr", r.accountKey)
        assertEquals("kaleb haile (2519****1879)", r.recipient); assertEquals("25/09/2026", r.transactionDate)
    }

    @Test fun telebirrReceivedFromCompanyWithDotsInName() {
        val r = parse("127", "Dear Nahom You have received ETB 5,000.00 from Hibret Trading P.L.C (2519****0001) on 01/09/2026 09:00:12. Your transaction number is CI12AB34CD. Your current E-Money Account balance is ETB 14,011.72.")
        assertEquals(5000.0, r.amount); assertEquals(FlowDirection.CREDIT, r.direction)
        assertEquals(TransactionType.INCOME, r.transactionType); assertEquals("Hibret Trading P.L.C (2519****0001)", r.sender)
    }

    @Test fun telebirrGoodsPurchase() {
        val r = parse("127", "Dear Nahom You have paid ETB 150.00 for goods purchased from 515151 - Ride Technologies on 20/09/2026 09:12:33. Your transaction number is DIQ12ABCD. Your current balance is ETB 861.72.")
        assertEquals(150.0, r.amount); assertEquals(FlowDirection.DEBIT, r.direction)
        assertEquals("515151 - Ride Technologies", r.merchant); assertEquals(861.72, r.balanceAfter)
    }

    @Test fun telebirrToBankTransfer() {
        val r = parse("127", "Dear Nahom You have transferred ETB 500.00 successfully from your telebirr account 251911223344 to Commercial Bank of Ethiopia account number 1000123456789 on 22/09/2026 10:11:12. Your telebirr transaction number is DIR1X2Y3Z4 and your bank transaction number is FT26265ABCD. The service fee is ETB 5.00 and 15% VAT on the service fee is ETB 0.75. Your current E-Money Account balance is ETB 355.97.")
        assertEquals(500.0, r.amount); assertEquals(5.75, r.fee, 0.001); assertEquals(FlowDirection.DEBIT, r.direction)
        assertEquals("DIR1X2Y3Z4", r.referenceNumber); assertEquals("telebirr_bank_transfer_builtin", r.detectedFormatId)
    }

    @Test fun telebirrAirtimeAndCommaDate() {
        val r = parse("127", "Dear Nahom, You have recharged ETB 25.00 airtime for 251911223344 on 23/09/2026 08:00:00, your transaction number is DIS1234567. Your current balance is ETB 330.97.")
        assertEquals(25.0, r.amount); assertEquals(FlowDirection.DEBIT, r.direction); assertEquals(330.97, r.balanceAfter)
    }

    @Test fun amharicReceivedUsesGenericFallback() {
        val r = parse("127", "ውድ ደንበኛ፣ ከ አበበ ከበደ (2519****1234) ብር 200.00 ተቀብለዋል። የግብይት ቁጥር DIT7654321። ቀሪ ሂሳብዎ ብር 530.97 ነው።")
        assertEquals(200.0, r.amount); assertEquals(FlowDirection.CREDIT, r.direction); assertEquals(530.97, r.balanceAfter)
    }

    @Test fun promotionIsNotATransaction() {
        val r = parse("127", "Pay ETB 100 with telebirr at any supermarket and get 10% cashback! Dial *127#")
        assertTrue("promo must not create a transaction: $r", r.amount == null || r.amount!! <= 0.0)
    }

    @Test fun cbeDebitSplitsBaseAndFeeWithoutDoubleCounting() {
        val r = parse("CBE", "Dear Nahom your Account 1*****0032 has been debited with ETB23,626.00 .Including Service charge and VAT(15%) with a total of ETB 23754.05. Your Current Balance is ETB 10,047.96. Thank you for Banking with CBE! https://apps.cbe.com.et:100/?id=FT26268AAAAA48220032")
        assertEquals(23626.0, r.amount); assertEquals(128.05, r.fee, 0.001)
        assertEquals("cbe:0032", r.accountKey); assertEquals(10047.96, r.balanceAfter)
    }

    @Test fun cbeReceivedUsesOwnAccountKey() {
        val r = parse("CBE", "Dear Nahom Tadele Worku You have received ETB 4,000.00 from account 1**2152 (Yonas Tadesse Girma) to your account 1**0032. Your current balance is ETB34,002.01. Thanks for Banking with CBE.")
        assertEquals("cbe:0032", r.accountKey); assertEquals("Yonas Tadesse Girma", r.sender); assertEquals(FlowDirection.CREDIT, r.direction)
    }

    @Test fun personalNumbersAreNeverBankSenders() {
        assertNull(SmsParserEngine.matchSenderIdentifier("+251912712345", "+251912712345", sources, identifiers))
        assertNull(SmsParserEngine.matchSenderIdentifier("0988912345", "0988912345", sources, identifiers))
        assertNull(SmsParserEngine.matchSenderIdentifier("Mom", "Mom", sources, identifiers))
        assertEquals("telebirr", SmsParserEngine.matchSenderIdentifier("127", "127", sources, identifiers)?.sourceId)
        assertEquals("cbe", SmsParserEngine.matchSenderIdentifier("889", "889", sources, identifiers)?.sourceId)
        assertEquals("cbe", SmsParserEngine.matchSenderIdentifier("CBE", "CBE", sources, identifiers)?.sourceId)
        assertEquals("boa", SmsParserEngine.matchSenderIdentifier("BankofAbyssinia", null, sources, identifiers)?.sourceId)
    }

    @Test fun relativesSharingANameAreNotInternalTransfers() {
        assertFalse(SmsParserEngine.isInternalTransfer("Tadele Abebe (2519****1111)", internal))
        assertFalse(SmsParserEngine.isInternalTransfer("Nahom Kebede", internal))
        assertTrue(SmsParserEngine.isInternalTransfer("Nahom Tadele Worku (2519****2222)", internal))
        assertTrue(SmsParserEngine.isInternalTransfer("NahomTadeleWorku", internal))
        val r = parse("127", "You have transferred ETB 300.00 to Tadele Abebe (2519****1111) on 25/09/2026 20:08:48. Your transaction number is DIP0000001. Your current E-Money Account balance is ETB 100.00.", internal)
        assertEquals(TransactionType.EXPENSE, r.transactionType)
    }

}
