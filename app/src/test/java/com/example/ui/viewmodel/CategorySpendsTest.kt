package com.example.ui.viewmodel

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySpendsTest {

    private val food = CategoryEntity("cat_food", "Food", "restaurant", "#F59E0B")
    private val rent = CategoryEntity("cat_rent", "Rent", "home", "#EC4899")
    private val transport = CategoryEntity("cat_transport", "Transport", "directions_car", "#3B82F6")

    private fun tx(id: String, amount: Double, categoryId: String?, direction: FlowDirection = FlowDirection.DEBIT, fee: Double = 0.0) =
        TransactionEntity(
            transactionId = id,
            phoneId = "P1",
            financialAccountId = "telebirr",
            sourceId = "telebirr",
            rawSmsId = id,
            transactionType = if (direction == FlowDirection.CREDIT) TransactionType.INCOME else TransactionType.EXPENSE,
            amount = amount,
            fee = fee,
            transactionDate = "01/09/2026",
            transactionTime = "10:00:00",
            categoryId = categoryId,
            direction = direction
        )

    @Test
    fun listsEveryCategoryAndSumsUncategorizedDebits() {
        val spends = MainViewModel.computeCategorySpends(
            listOf(food, rent, transport),
            listOf(
                tx("1", 100.0, "cat_food", fee = 2.0),
                tx("2", 300.0, "cat_rent"),
                tx("3", 50.0, null),
                tx("4", 48.0, ""),
                tx("5", 1_000.0, "inc_shop", direction = FlowDirection.CREDIT) // credits are not spending
            )
        )

        val pending = spends.first()
        assertTrue(pending.isUncategorized)
        assertEquals(98.0, pending.totalAmount, 0.001)
        assertEquals(2, pending.transactionCount)

        val rest = spends.drop(1)
        assertEquals(listOf("cat_rent", "cat_food", "cat_transport"), rest.map { it.category.categoryId })
        assertEquals(102.0, rest[1].totalAmount, 0.001)
        assertEquals(0.0, rest[2].totalAmount, 0.001)
        assertEquals(1f, spends.sumOf { it.percentage.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun noUncategorizedRowOnceEverythingIsCategorized() {
        val spends = MainViewModel.computeCategorySpends(listOf(food), listOf(tx("1", 10.0, "cat_food")))
        assertEquals(1, spends.size)
        assertFalse(spends.single().isUncategorized)
    }
}
