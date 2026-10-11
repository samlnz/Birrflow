package com.example.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class CapitalPlanDeadlineTest {

    private val now = 1_760_000_000_000L
    private val day = 24L * 3600 * 1000

    @Test
    fun usesUpperBoundOfTheTimeframe() {
        assertEquals(now + 14 * day, MainViewModel.deadlineFromTimeframe("1-2 weeks", now))
        assertEquals(now + 30 * day, MainViewModel.deadlineFromTimeframe("1 month", now))
        assertEquals(now + 90 * day, MainViewModel.deadlineFromTimeframe("3 months", now))
        assertEquals(now + 10 * day, MainViewModel.deadlineFromTimeframe("7-10 days", now))
        assertEquals(now + 7 * day, MainViewModel.deadlineFromTimeframe("Immediate", now))
        assertEquals(now + 30 * day, MainViewModel.deadlineFromTimeframe("ongoing", now))
    }
}
