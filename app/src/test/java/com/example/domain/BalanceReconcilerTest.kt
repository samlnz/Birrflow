package com.example.domain

import com.example.data.entity.FlowDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceReconcilerTest {
    private fun e(id: String, t: Long, d: FlowDirection, a: Double, bal: Double?, fee: Double = 0.0) = BalanceReconciler.Entry(id, t, d, a, fee, bal)

    @Test fun reconcilerFindsMissingCreditAndDebit() {
        val gaps = BalanceReconciler.findGaps(listOf(
            e("a", 1, FlowDirection.DEBIT, 1000.0, 9011.72, 4.0),   // before: 10015.72
            e("b", 2, FlowDirection.CREDIT, 50.0, 9561.72),         // expected before 9511.72 -> missing credit 500
            e("c", 3, FlowDirection.DEBIT, 100.0, 9400.0),          // expected before 9500 -> missing debit 61.72
            e("d", 4, FlowDirection.CREDIT, 100.0, 9500.0)          // consistent
        ))
        assertEquals(2, gaps.size)
        assertEquals(FlowDirection.CREDIT, gaps[0].direction); assertEquals(500.0, gaps[0].amount, 0.001); assertEquals("b", gaps[0].beforeEventId)
        assertEquals(FlowDirection.DEBIT, gaps[1].direction); assertEquals(61.72, gaps[1].amount, 0.001)
    }

    @Test fun reconcilerHandlesOutOfOrderDelivery() {
        val gaps = BalanceReconciler.findGaps(listOf(
            e("a", 1_000, FlowDirection.CREDIT, 100.0, 1100.0),
            e("c", 2_000, FlowDirection.DEBIT, 50.0, 1250.0),  // actually happened after b
            e("b", 3_000, FlowDirection.CREDIT, 200.0, 1300.0)
        ))
        assertTrue("swapped delivery must not create fake gaps: $gaps", gaps.isEmpty())
    }

    @Test fun reconcilerUsesUnparsedSmsBalanceAsAnchor() {
        val gaps = BalanceReconciler.findGaps(
            listOf(e("a", 1, FlowDirection.CREDIT, 100.0, 1100.0), e("b", 5, FlowDirection.DEBIT, 10.0, 690.0)),
            listOf(BalanceReconciler.Anchor("raw-1", 3, 700.0))
        )
        assertEquals(1, gaps.size); assertEquals("raw-1", gaps[0].unparsedRawSmsId); assertEquals(400.0, gaps[0].amount, 0.001)
    }

}
